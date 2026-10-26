import { useEffect, useRef, useState, type FormEvent } from "react";
import { getUsers, promoteUser, type ManagedUser } from "./adminApi";

function PromotionDialog({ user, onClose, onPromoted }: { user: ManagedUser; onClose: () => void; onPromoted: (user: ManagedUser) => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    useEffect(() => {
        dialog.current?.showModal();
    }, []);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (busy) return;
        setBusy(true);
        setError(null);
        try {
            onPromoted(await promoteUser(user.id));
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not grant admin access. Please try again.");
        } finally {
            setBusy(false);
        }
    };

    return (
        <dialog
            ref={dialog}
            className="detail-modal promotion-dialog"
            aria-labelledby="promotion-heading"
            onClose={onClose}
            onCancel={(event) => {
                if (busy) event.preventDefault();
            }}
        >
            <form onSubmit={(event) => void submit(event)}>
                <h2 id="promotion-heading">Grant admin access?</h2>
                <p>
                    <strong>{user.displayName}</strong>
                    <br />
                    {user.email}
                </p>
                <p>Admins can manage workspace knowledge and grant admin access to other users. This user must sign out and sign in again to activate the new role.</p>
                {error && (
                    <p className="upload-error" role="alert">
                        {error}
                    </p>
                )}
                <div className="admin-actions">
                    <button className="secondary-button" type="button" disabled={busy} onClick={() => dialog.current?.close()}>
                        Cancel
                    </button>
                    <button className="primary-button" type="submit" disabled={busy}>
                        {busy ? "Granting access…" : "Confirm grant admin"}
                    </button>
                </div>
            </form>
        </dialog>
    );
}

export function AdminPage() {
    const [users, setUsers] = useState<ManagedUser[]>([]);
    const [search, setSearch] = useState("");
    const [selected, setSelected] = useState<ManagedUser | null>(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [message, setMessage] = useState<string | null>(null);
    const [reload, setReload] = useState(0);
    useEffect(() => {
        let active = true;
        void getUsers()
            .then((value) => {
                if (active) {
                    setUsers(value);
                    setError(null);
                }
            })
            .catch((reason: unknown) => {
                if (active) setError(reason instanceof Error ? reason.message : "We could not load users. Please try again.");
            })
            .finally(() => {
                if (active) setLoading(false);
            });
        return () => {
            active = false;
        };
    }, [reload]);
    const filtered = users.filter((user) => `${user.displayName} ${user.email}`.toLowerCase().includes(search.trim().toLowerCase()));

    return (
        <section className="collection panel admin-users" aria-labelledby="admin-users-heading">
            <div className="collection-heading">
                <div>
                    <h2 id="admin-users-heading">User access</h2>
                    <p>Select an enabled user to grant admin privileges.</p>
                </div>
                <button
                    className="secondary-button"
                    type="button"
                    disabled={loading}
                    onClick={() => {
                        setLoading(true);
                        setReload((value) => value + 1);
                    }}
                >
                    {loading ? "Loading users…" : "Refresh users"}
                </button>
            </div>
            <label htmlFor="admin-user-search">Find a user</label>
            <input id="admin-user-search" type="search" placeholder="Search by name or email" value={search} onChange={(event) => setSearch(event.target.value)} />
            {error && (
                <p className="upload-error" role="alert">
                    {error}
                </p>
            )}
            {message && (
                <p className="admin-success" role="status">
                    {message}
                </p>
            )}
            {loading && <p role="status">Loading user accounts…</p>}
            {!loading && !error && filtered.length === 0 && <p>No users match your search.</p>}
            <ul className="admin-user-list" aria-label="User accounts">
                {filtered.map((user) => (
                    <li key={user.id}>
                        <div className="admin-user-identity">
                            <h3>{user.displayName}</h3>
                            <p>{user.email}</p>
                            <small>
                                {user.role === "ADMIN" ? "Admin" : "User"} · {user.enabled ? "Enabled" : "Disabled"}
                            </small>
                        </div>
                        {user.role === "USER" && (
                            <button
                                className="secondary-button"
                                type="button"
                                disabled={!user.enabled || loading || error !== null}
                                aria-label={`Grant admin to ${user.email}`}
                                onClick={() => {
                                    setSelected(user);
                                    setMessage(null);
                                }}
                            >
                                Grant admin
                            </button>
                        )}
                    </li>
                ))}
            </ul>
            {selected && (
                <PromotionDialog
                    user={selected}
                    onClose={() => setSelected(null)}
                    onPromoted={(user) => {
                        setUsers((current) => current.map((entry) => (entry.id === user.id ? user : entry)));
                        setMessage(`Admin access granted to ${user.displayName} (${user.email}). They must sign out and sign in again to activate the new role.`);
                        setSelected(null);
                    }}
                />
            )}
        </section>
    );
}
