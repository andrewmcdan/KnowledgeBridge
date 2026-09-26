import type { FormEvent } from "react";
import { Icon } from "../ui/Icon";
import type { Detail } from "../../types/models";

interface TopbarProps {
    search: string;
    onSearchChange: (value: string) => void;
    onSearchSubmit: () => void;
    onToggleNavigation: () => void;
    onOpenDetail: (detail: Detail) => void;
}

export function Topbar({ search, onSearchChange, onSearchSubmit, onToggleNavigation, onOpenDetail }: TopbarProps) {
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
                <input id="global-search" aria-label="Search sample knowledge base" placeholder="Search knowledge base, documents, entities…" value={search} onChange={(event) => onSearchChange(event.target.value)} />
                <kbd>⌘ K</kbd>
            </form>
            <button className="organization" onClick={() => onOpenDetail({ title: "Acme Corporation", text: "Acme Corporation is the fictional organization used in this interface preview. Organization switching will be available when accounts are connected." })}>
                <Icon name="home" size={18} />
                <span>Acme Corporation</span>
                <span>⌄</span>
            </button>
            <button className="profile" onClick={() => onOpenDetail({ title: "Demo workspace", text: "You are viewing the frontend scaffold. Login, user profiles, and role-based access are not connected yet." })}>
                <span className="avatar">AC</span>
                <span>Demo user</span>
                <span>⌄</span>
            </button>
        </header>
    );
}
