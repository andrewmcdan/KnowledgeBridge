// Runs inside the pinned Bun image with only the selected volume mounted at /purge.
import { lstat, readdir, rm } from "node:fs/promises";
import { resolve, relative, sep, join } from "node:path";

const [kind, action, directory = "/purge"] = process.argv.slice(2);
if (!["uploads", "engine"].includes(kind) || !["preview", "apply"].includes(action)) throw new Error("Invalid purge operation");
const root = resolve(directory);
if (root === resolve(root, "..")) throw new Error("Refusing a filesystem root");
const entries = [];

function preserved(path) {
    // Engine configuration and local writer credentials contain no document bodies.
    return kind === "engine" && (path === ".gbrain/config.json" || /^\.gbrain\/persistence\/(host|[a-f0-9-]+\.(cli|stdio))\.json$/.test(path));
}

async function inspect(path) {
    const inside = relative(root, path);
    if (inside === ".." || inside.startsWith(`..${sep}`) || resolve(root, inside) !== path) throw new Error("Storage path escapes its root");
    const stat = await lstat(path);
    if (stat.isSymbolicLink()) throw new Error("Storage contains a symbolic link; inspect it before purging");
    if (stat.isDirectory()) {
        for (const entry of await readdir(path)) await inspect(join(path, entry));
        if (inside) entries.push({ path, directory: true, keep: false, bytes: 0 });
    } else {
        if (!stat.isFile() && !(kind === "engine" && inside.split(sep).join("/").startsWith(".gbrain/run/"))) throw new Error("Storage contains a special file; inspect it before purging");
        entries.push({ path, directory: false, keep: preserved(inside.split(sep).join("/")), bytes: stat.size });
    }
}

await inspect(root);
const removed = entries.filter((entry) => !entry.directory && !entry.keep);
if (action === "apply") {
    for (const entry of removed) await rm(entry.path);
    for (const entry of entries.filter((item) => item.directory)) {
        if ((await readdir(entry.path)).length === 0) await rm(entry.path, { recursive: true });
    }
}
console.log(JSON.stringify({ files: removed.length, bytes: removed.reduce((sum, entry) => sum + entry.bytes, 0), preserved: entries.filter((entry) => entry.keep).length }));
