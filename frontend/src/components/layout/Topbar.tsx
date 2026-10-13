import type { FormEvent } from "react";
import { Icon } from "../ui/Icon";
import type { AuthUser } from "../../features/auth/authApi";

interface TopbarProps {
    search: string;
    onSearchChange: (value: string) => void;
    onSearchSubmit: () => void;
    onToggleNavigation: () => void;
    onLogout: () => void;
    user: AuthUser;
}

function initials(name: string): string {
    return name
        .split(/\s+/)
        .filter(Boolean)
        .slice(0, 2)
        .map((part) => part[0]?.toUpperCase())
        .join("");
}

export function Topbar({ search, onSearchChange, onSearchSubmit, onToggleNavigation, onLogout, user }: TopbarProps) {
    const submit = (event: FormEvent) => {
        event.preventDefault();
        onSearchSubmit();
    };
    return (
        <header className="topbar">
            <button className="icon-button menu-button" aria-label="Toggle navigation" onClick={onToggleNavigation}>
                <Icon name="menu" />
            </button>
            <form className="global-search" onSubmit={submit}>
                <Icon name="search" size={18} />
                <input id="global-search" aria-label="Search knowledge base" placeholder="Search knowledge base…" value={search} onChange={(event) => onSearchChange(event.target.value)} />
                <kbd>⌘ K</kbd>
            </form>
            <div className="organization">
                <Icon name="home" size={18} />
                <span>Workspace</span>
            </div>
            <details className="profile-menu">
                <summary className="profile">
                    <span className="avatar">{initials(user.displayName)}</span>
                    <span>{user.displayName}</span>
                    <span aria-hidden="true">⌄</span>
                </summary>
                <div className="profile-popover">
                    <p>{user.email}</p>
                    <button onClick={onLogout}>Sign out</button>
                </div>
            </details>
        </header>
    );
}
