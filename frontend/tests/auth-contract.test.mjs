import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

const packageRequire = createRequire(import.meta.url);

function loadApi(file, fetch) {
    const window = new EventTarget();
    const modules = new Map();
    function load(url) {
        if (modules.has(url.href)) return modules.get(url.href);
        const module = { exports: {} };
        modules.set(url.href, module.exports);
        const source = readFileSync(url, "utf8");
        const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } });
        runInNewContext(outputText, {
            module,
            exports: module.exports,
            require: (specifier) => (specifier.startsWith(".") ? load(new URL(`${specifier}.ts`, url)) : packageRequire(specifier)),
            fetch,
            window,
            Event,
            CustomEvent,
            FormData,
            TextEncoder,
            URLSearchParams,
            sessionStorage: { getItem: () => "signed-token" },
            localStorage: { getItem: () => null },
        });
        return module.exports;
    }
    return { api: load(new URL(`../src/${file}.ts`, import.meta.url)), window };
}

const user = { id: 2, email: "user@acme.example", displayName: "Acme User", role: "USER" };

test("login and me agree on the identity and role contract", async () => {
    const calls = [];
    const { api } = loadApi("features/auth/authApi", async (path, options) => {
        calls.push({ path, options });
        return Response.json(path.endsWith("/login") ? { token: "signed-token", expiresAt: "2026-10-26T00:00:00Z", user } : user);
    });
    const login = await api.login("user@acme.example", "password");
    assert.equal(login.user.role, "USER");
    assert.equal(calls[0].path, "/api/auth/login");
    assert.deepEqual(JSON.parse(calls[0].options.body), { email: "user@acme.example", password: "password" });
    assert.equal((await api.getCurrentUser(login.token)).id, 2);
    assert.equal(calls[1].options.headers.Authorization, "Bearer signed-token");
});

function setupAuthProvider(getCurrentUser = async () => user) {
    const window = new EventTarget();
    const stored = new Map([["knowledgebridge.auth.token", "old-token"]]);
    const storage = {
        getItem: (key) => stored.get(key) ?? null,
        setItem: (key, value) => stored.set(key, value),
        removeItem: (key) => stored.delete(key),
    };
    const sessionStorage = storage;
    const localStorage = { getItem: () => null, removeItem: () => {} };
    const state = [];
    const effects = [];
    let actions;
    const apiModule = { exports: {} };
    const apiSource = readFileSync(new URL("../src/api/apiError.ts", import.meta.url), "utf8");
    runInNewContext(ts.transpileModule(apiSource, { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText, {
        module: apiModule,
        exports: apiModule.exports,
        window,
        Event,
        CustomEvent,
    });
    const providerModule = { exports: {} };
    const source = readFileSync(new URL("../src/features/auth/AuthProvider.tsx", import.meta.url), "utf8");
    runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX } }).outputText, {
        module: providerModule,
        exports: providerModule.exports,
        window,
        sessionStorage,
        localStorage,
        require: (specifier) => {
            if (specifier === "react") {
                return {
                    useState: (initial) => {
                        const index = state.length;
                        state.push(typeof initial === "function" ? initial() : initial);
                        return [state[index], (value) => (state[index] = value)];
                    },
                    useEffect: (effect) => effects.push(effect),
                    useMemo: (factory) => (actions = factory()),
                };
            }
            if (specifier === "react/jsx-runtime") return { jsx: () => null };
            if (specifier === "./AuthContext") return { authContext: { Provider: () => null } };
            if (specifier === "../../api/apiError") return apiModule.exports;
            if (specifier === "./authApi") {
                return {
                    getCurrentUser,
                    register: async () => ({ token: "registration-token", user }),
                    login: async () => ({ token: "new-token", user: { ...user, role: "ADMIN" } }),
                    logout: async () => {
                        throw new Error("Server unavailable");
                    },
                };
            }
            throw new Error(`Unexpected import ${specifier}`);
        },
    });
    providerModule.exports.AuthProvider({ children: null });
    const cleanups = effects.map((effect) => effect());
    return { actions, state, stored, api: apiModule.exports, cleanup: () => cleanups.forEach((cleanup) => cleanup?.()) };
}

test("a delayed 401 from an old session cannot sign out a newer login", async () => {
    const auth = setupAuthProvider();
    await new Promise((resolve) => setImmediate(resolve));
    await auth.actions.signIn("admin@acme.example", "password", false);
    await auth.api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Old session expired." }, { status: 401 }), "fallback", "Bearer old-token");
    assert.equal(auth.stored.get("knowledgebridge.auth.token"), "new-token");
    assert.equal(auth.state[0], "authenticated");
    assert.equal(auth.state[1].role, "ADMIN");
    auth.cleanup();
});

test("a 401 for the active session signs out, and failed logout still clears credentials", async () => {
    const auth = setupAuthProvider();
    await new Promise((resolve) => setImmediate(resolve));
    await auth.api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Expired." }, { status: 401 }), "fallback", "Bearer old-token");
    assert.equal(auth.state[0], "unauthenticated");
    assert.equal(auth.state[1], null);
    assert.equal(auth.stored.size, 0);
    await auth.actions.signIn("admin@acme.example", "password", false);
    await auth.actions.signOut();
    assert.equal(auth.state[0], "unauthenticated");
    assert.equal(auth.state[1], null);
    assert.equal(auth.stored.size, 0);
    auth.cleanup();
});

test("a rejected session-restore request cannot clear a replacement login", async () => {
    let rejectRestore;
    const auth = setupAuthProvider(() => new Promise((_resolve, reject) => (rejectRestore = reject)));
    await auth.api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Old session expired." }, { status: 401 }), "fallback", "Bearer old-token");
    await auth.actions.signIn("admin@acme.example", "password", false);
    rejectRestore(new Error("The old restore request failed"));
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(auth.stored.get("knowledgebridge.auth.token"), "new-token");
    assert.equal(auth.state[0], "authenticated");
    assert.equal(auth.state[1].role, "ADMIN");
    auth.cleanup();
});

test("logout uses POST with the captured token and accepts an empty 204", async () => {
    const { api } = loadApi("features/auth/authApi", async (path, options) => {
        assert.equal(path, "/api/auth/logout");
        assert.equal(options.method, "POST");
        assert.equal(options.headers.Authorization, "Bearer signed-token");
        return new Response(null, { status: 204 });
    });
    await api.logout("signed-token");
});

test("invalid identity payloads and rejected credentials are not accepted as sessions", async () => {
    const invalid = loadApi("features/auth/authApi", async () => Response.json({ ...user, role: "OWNER" })).api;
    await assert.rejects(invalid.getCurrentUser("token"), { message: "The server returned an invalid user profile." });
    await assert.rejects(invalid.login("user@acme.example", "password"), { message: "The server returned an invalid sign-in response." });
    const rejected = loadApi("features/auth/authApi", async () => Response.json({ code: "UNAUTHENTICATED", message: "Invalid credentials." }, { status: 401 })).api;
    await assert.rejects(rejected.login("user@acme.example", "wrong"), { message: "The email address or password is incorrect." });
});

test("API errors use the shared envelope, discard raw HTML, and expire rejected sessions", async () => {
    const { api, window } = loadApi("api/apiError", async () => {});
    let expired = 0;
    window.addEventListener(api.AUTH_EXPIRED_EVENT, () => expired++);
    const error = await api.readApiError(Response.json({ code: "FORBIDDEN", message: "Admin access is required." }, { status: 403 }), "fallback");
    assert.equal(error.message, "Admin access is required.");
    assert.equal(expired, 0);
    assert.equal((await api.readApiError(new Response("<html>proxy error</html>", { status: 502 }), "Safe fallback")).message, "Safe fallback");
    await api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Sign in again." }, { status: 401 }), "fallback", "Bearer active-token");
    assert.equal(expired, 1);
    await api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Old logout token." }, { status: 401 }), "fallback");
    assert.equal(expired, 1);
    assert.equal((await api.readApiError(Response.json({ message: "No code" }, { status: 400 }), "Safe fallback")).message, "Safe fallback");
});

test("knowledge requests and response guards accept the committed contract example", async () => {
    const contract = JSON.parse(readFileSync(new URL("../../Docs/openapi.json", import.meta.url), "utf8"));
    const item = contract.components.schemas.KnowledgeItem.example;
    const calls = [];
    const { api } = loadApi("features/knowledge/knowledgeApi", async (path, options) => {
        calls.push({ path, options });
        return Response.json(path === "/api/knowledge-items" ? [item] : item);
    });
    assert.equal((await api.getKnowledgeItems())[0].status, "COMPLETED");
    await api.createManualKnowledgeItem("Title", "Note", "Body");
    assert.deepEqual(JSON.parse(calls[1].options.body), { title: "Title", type: "Note", text: "Body" });
    const file = new File(["# Body"], "note.md", { type: "text/markdown" });
    await api.uploadKnowledgeFile(file, "Title", "Note");
    assert.equal(calls[2].options.body.get("file").name, "note.md");
    assert.equal(calls[2].options.body.get("title"), "Title");
    assert.equal(calls[2].options.body.get("type"), "Note");
    assert.equal(calls[2].options.headers["Content-Type"], undefined);
    await api.retryKnowledgeItem(1);
    assert.equal(calls[3].path, "/api/knowledge-items/1/retry-ingestion");
    assert.equal(calls[3].options.method, "POST");
    assert.equal(calls[3].options.body, undefined);
    assert.ok(calls.every((call) => call.options.headers.Authorization === "Bearer signed-token"));
});

test("protected APIs associate rejected responses with the token sent on that request", async () => {
    const scenarios = [
        ["features/auth/authApi", (api) => api.getCurrentUser("signed-token")],
        ["features/knowledge/knowledgeApi", (api) => api.getKnowledgeItems()],
        ["features/knowledge/knowledgeApi", (api) => api.createManualKnowledgeItem("Title", "Note", "Body")],
        ["features/knowledge/knowledgeApi", (api) => api.uploadKnowledgeFile(new File(["Body"], "note.md"), "Title", "Note")],
        ["features/knowledge/knowledgeApi", (api) => api.uploadKnowledgeFile(new File(["Body"], "talk.mp4"), "Title", "Video")],
        ["features/knowledge/knowledgeApi", (api) => api.retryKnowledgeItem(1)],
        ["features/ask-ai/askAiApi", (api) => api.submitQuestion("Search", "Question")],
        ["features/entities/entitiesApi", (api) => api.getEntities()],
    ];
    for (const [file, invoke] of scenarios) {
        const { api, window } = loadApi(file, async () => Response.json({ code: "UNAUTHENTICATED", message: "Expired." }, { status: 401 }));
        const rejected = [];
        window.addEventListener("knowledgebridge:auth-expired", (event) => rejected.push(event.detail));
        await assert.rejects(invoke(api), { message: "Expired." });
        assert.deepEqual(rejected, ["Bearer signed-token"], file);
    }
});

test("registration sends only identity fields and preserves the exact password", async () => {
    const { api } = loadApi("features/auth/authApi", async (path, options) => {
        assert.equal(path, "/api/auth/register");
        assert.equal(options.method, "POST");
        assert.equal(options.headers["Content-Type"], "application/json");
        assert.deepEqual(JSON.parse(options.body), { displayName: "New Reader", email: "READER@example.test", password: "  exact password  " });
        return Response.json({ token: "created-token", expiresAt: "2026-10-26T00:00:00Z", user }, { status: 201 });
    });
    assert.equal((await api.register(" New Reader ", " READER@example.test ", "  exact password  ")).user.role, "USER");
});

test("registration reports duplicates safely and rejects invalid or privileged sessions", async () => {
    const duplicate = loadApi("features/auth/authApi", async () => Response.json({ code: "EMAIL_ALREADY_REGISTERED", message: "An account with this email already exists. Sign in instead." }, { status: 409 })).api;
    await assert.rejects(duplicate.register("Reader", "reader@example.test", "password"), /Sign in instead/);
    const unavailable = loadApi("features/auth/authApi", async () => new Response("<html>private error</html>", { status: 500 })).api;
    await assert.rejects(unavailable.register("Reader", "reader@example.test", "password"), /We could not create your account/);
    for (const payload of [null, {}, { token: "token", expiresAt: "date", user: { ...user, role: "ADMIN" } }]) {
        const invalid = loadApi("features/auth/authApi", async () => Response.json(payload)).api;
        await assert.rejects(invalid.register("Reader", "reader@example.test", "password"), /invalid registration response/);
    }
});

test("registration authenticates in session storage, survives old responses, and logs out", async () => {
    const auth = setupAuthProvider();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(auth.state[0], "authenticated", "stored session restores on mount");
    await auth.actions.signUp("Reader", "reader@example.test", "password");
    assert.equal(auth.stored.get("knowledgebridge.auth.token"), "registration-token");
    assert.equal(auth.state[0], "authenticated");
    assert.equal(auth.state[1].role, "USER");
    await auth.api.readApiError(Response.json({ code: "UNAUTHENTICATED", message: "Expired." }, { status: 401 }), "fallback", "Bearer old-token");
    assert.equal(auth.stored.get("knowledgebridge.auth.token"), "registration-token");
    await auth.actions.signOut();
    assert.equal(auth.stored.size, 0);
    assert.equal(auth.state[0], "unauthenticated");
    auth.cleanup();
});

test("registration validation counts Unicode characters and UTF-8 bytes without trimming passwords", () => {
    const { validateRegistration } = loadApi("features/auth/registration", async () => {}).api;
    for (const password of ["12345678", "        ", "  exact password  ", "a".repeat(72), "😀".repeat(8), "😀".repeat(18)]) {
        assert.equal(validateRegistration(" Reader ", " reader@example.test ", password, password), null);
    }
    assert.match(validateRegistration(" ", "email", "password", "password"), /Enter your name/);
    assert.match(validateRegistration("x".repeat(256), "email", "password", "password"), /255/);
    assert.match(validateRegistration("Reader", " ", "password", "password"), /Enter your email/);
    assert.match(validateRegistration("Reader", "x".repeat(256), "password", "password"), /255/);
    for (const password of ["", "1234567", "😀".repeat(4)]) assert.match(validateRegistration("Reader", "email", password, password), /eight/);
    for (const password of ["a".repeat(73), "😀".repeat(19)]) assert.match(validateRegistration("Reader", "email", password, password), /72 UTF-8 bytes/);
    assert.match(validateRegistration("Reader", "email", " password ", "password"), /do not match/);
});

test("admin user management sends authenticated list and promotion requests without editable roles", async () => {
    const managed = { ...user, enabled: true };
    const calls = [];
    const { api } = loadApi("features/admin/adminApi", async (path, options) => {
        calls.push({ path, options });
        return Response.json(options.method === "POST" ? { ...managed, role: "ADMIN" } : [managed]);
    });
    assert.equal((await api.getUsers())[0].id, user.id);
    assert.equal((await api.promoteUser(user.id)).role, "ADMIN");
    assert.equal(calls[0].path, "/api/admin/users");
    assert.equal(calls[1].path, `/api/admin/users/${user.id}/promote`);
    assert.equal(calls[1].options.method, "POST");
    assert.equal(calls[1].options.body, undefined);
    assert.ok(calls.every((call) => call.options.headers.Authorization === "Bearer signed-token"));
});

test("admin APIs reject malformed identities, wrong promotion targets and invalid ids", async () => {
    const managed = { ...user, enabled: true };
    for (const value of [null, {}, { ...managed, id: -1 }, { ...managed, id: 1.5 }, { ...managed, role: "OWNER" }, { ...managed, enabled: "true" }]) {
        const { api } = loadApi("features/admin/adminApi", async () => Response.json([value]));
        await assert.rejects(api.getUsers(), /invalid user list/);
    }
    const notArray = loadApi("features/admin/adminApi", async () => Response.json(managed)).api;
    await assert.rejects(notArray.getUsers(), /invalid user list/);
    for (const value of [null, managed, { ...managed, role: "ADMIN", id: 99 }, { ...managed, role: "ADMIN", enabled: false }]) {
        const { api } = loadApi("features/admin/adminApi", async () => Response.json(value));
        await assert.rejects(api.promoteUser(user.id), /invalid promotion response/);
    }
    const invalidId = loadApi("features/admin/adminApi", () => {
        throw new Error("Must not fetch");
    }).api;
    for (const id of [0, -1, 1.5, NaN]) await assert.rejects(invalidId.promoteUser(id), /Select a valid user/);
});

test("admin APIs surface permission and conflict errors and expire only the rejected session", async () => {
    for (const [status, code, message] of [
        [403, "FORBIDDEN", "Admin access required."],
        [404, "NOT_FOUND", "User not found."],
        [409, "CONFLICT", "Disabled account."],
    ]) {
        const { api } = loadApi("features/admin/adminApi", async () => Response.json({ code, message }, { status }));
        await assert.rejects(api.promoteUser(user.id), { message });
        await assert.rejects(api.getUsers(), { message });
    }
    const { api, window } = loadApi("features/admin/adminApi", async () => Response.json({ code: "UNAUTHENTICATED", message: "Expired" }, { status: 401 }));
    const expired = [];
    window.addEventListener("knowledgebridge:auth-expired", (event) => expired.push(event.detail));
    await assert.rejects(api.promoteUser(user.id), /Expired/);
    assert.deepEqual(expired, ["Bearer signed-token"]);
});
