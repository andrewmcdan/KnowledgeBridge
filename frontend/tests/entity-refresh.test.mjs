import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import test from "node:test";
import { runInNewContext } from "node:vm";
import ts from "typescript";

function setup(load) {
    let cleanup;
    let nextTimer = 0;
    const timers = new Map();
    const window = new EventTarget();
    const document = new EventTarget();
    document.visibilityState = "visible";
    window.setTimeout = (callback, delay) => {
        timers.set(++nextTimer, { callback, delay });
        return nextTimer;
    };
    window.clearTimeout = (id) => timers.delete(id);
    const module = { exports: {} };
    const source = readFileSync(new URL("../src/features/entities/useEntityRefresh.ts", import.meta.url), "utf8");
    const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS } });
    runInNewContext(outputText, {
        module,
        exports: module.exports,
        require: () => ({ useEffect: (effect) => (cleanup = effect()) }),
        window,
        document,
        AbortController,
    });
    module.exports.useEntityRefresh(load, 200);
    return {
        window,
        document,
        timers,
        cleanup: () => cleanup(),
        tick: async () => {
            const [id, timer] = timers.entries().next().value;
            timers.delete(id);
            timer.callback();
            await new Promise((resolve) => setImmediate(resolve));
        },
    };
}

test("loads initially, polls every ten seconds, and pauses while hidden", async () => {
    const calls = [];
    const state = setup(async (_signal, initial) => calls.push(initial));
    assert.equal([...state.timers.values()][0].delay, 200);
    await state.tick();
    assert.deepEqual(calls, [true]);
    assert.equal([...state.timers.values()][0].delay, 10_000);
    await state.tick();
    assert.deepEqual(calls, [true, false]);
    state.document.visibilityState = "hidden";
    await state.tick();
    assert.equal(calls.length, 2);
    state.document.visibilityState = "visible";
    state.document.dispatchEvent(new Event("visibilitychange"));
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(calls.length, 3);
    state.window.dispatchEvent(new Event("knowledgebridge:entities-updated"));
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(calls.length, 4);
    state.window.dispatchEvent(new Event("focus"));
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(calls.length, 5);
    state.cleanup();
    assert.equal(state.timers.size, 0);
    state.window.dispatchEvent(new Event("focus"));
    assert.equal(calls.length, 5);
});

test("does not overlap requests and aborts pending work on cleanup", async () => {
    let signal;
    let finish;
    let calls = 0;
    const state = setup((currentSignal) => {
        signal = currentSignal;
        calls++;
        return new Promise((resolve) => (finish = resolve));
    });
    await state.tick();
    state.window.dispatchEvent(new Event("focus"));
    state.window.dispatchEvent(new Event("knowledgebridge:entities-updated"));
    assert.equal(calls, 1);
    state.cleanup();
    assert.equal(signal.aborted, true);
    finish();
    await new Promise((resolve) => setImmediate(resolve));
    assert.equal(state.timers.size, 0);
});
