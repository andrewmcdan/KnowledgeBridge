import { NavLink } from "react-router-dom";
import wordmark from "../../assets/wordmark.png";
import { navigation } from "../../data/navigation";
import { Icon } from "../ui/Icon";

interface SidebarProps {
    collapsed: boolean;
    isAdmin: boolean;
    mobileOpen: boolean;
    service: string;
    onNavigate: () => void;
    onToggleCollapsed: () => void;
}

export function Sidebar({ collapsed, isAdmin, mobileOpen, service, onNavigate, onToggleCollapsed }: SidebarProps) {
    return (
        <aside className={`sidebar ${mobileOpen ? "mobile-open" : ""}`}>
            <NavLink className="brand" to="/ask-ai" aria-label="KnowledgeBridge home" onClick={onNavigate}>
                <img className="wordmark" src={wordmark} alt="KnowledgeBridge" />
                <img className="compact-logo" src="/logo.png" alt="" />
            </NavLink>
            <nav aria-label="Main navigation">
                {navigation
                    .filter((item) => !item.requiresAdmin || isAdmin)
                    .map((item) => (
                        <NavLink key={item.path} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`} to={item.path} onClick={onNavigate} title={item.label}>
                            <Icon name={item.icon} />
                            <span>{item.label}</span>
                            {item.label === "Admin" && <span className="nav-chevron">⌄</span>}
                        </NavLink>
                    ))}
            </nav>
            <section className="summary panel">
                <h2>
                    <Icon name="chart" size={17} /> Workspace summary
                </h2>
                {/* TODO: Load workspace metrics from a live API endpoint. */}
                <p className="summary-empty">Live workspace metrics will appear here when available.</p>
            </section>
            <div className="sidebar-connection" title={service}>
                <span className={service === "API & database connected" ? "connected" : ""} />
                <span className="sidebar-connection-label">{service}</span>
            </div>
            <button className="collapse-button" onClick={onToggleCollapsed} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
                <span>{collapsed ? "›" : "‹"}</span>
                <span className="collapse-label">Collapse</span>
            </button>
        </aside>
    );
}
