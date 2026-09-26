import { NavLink } from "react-router-dom";
import wordmark from "../../assets/wordmark.png";
import { navigation, summaryMetrics } from "../../data/demoData";
import { Icon } from "../ui/Icon";

interface SidebarProps {
    collapsed: boolean;
    mobileOpen: boolean;
    onNavigate: () => void;
    onToggleCollapsed: () => void;
}

export function Sidebar({ collapsed, mobileOpen, onNavigate, onToggleCollapsed }: SidebarProps) {
    return (
        <aside className={`sidebar ${mobileOpen ? "mobile-open" : ""}`}>
            <NavLink className="brand" to="/ask-ai" aria-label="KnowledgeBridge home" onClick={onNavigate}>
                <img className="wordmark" src={wordmark} alt="KnowledgeBridge" />
                <img className="compact-logo" src="/logo.png" alt="" />
            </NavLink>
            <nav aria-label="Main navigation">
                {navigation.map((item) => (
                    <NavLink key={item.path} className={({ isActive }) => `nav-item ${isActive ? "active" : ""}`} to={item.path} onClick={onNavigate} title={item.label}>
                        <Icon name={item.icon} />
                        <span>{item.label}</span>
                        {item.label === "Admin" && <span className="nav-chevron">⌄</span>}
                    </NavLink>
                ))}
            </nav>
            <section className="summary panel">
                <h2>
                    <Icon name="chart" size={17} /> Today's Summary <span className="demo-label">Demo</span>
                </h2>
                {summaryMetrics.map(([label, value, change]) => (
                    <div className="metric" key={label}>
                        <small>{label}</small>
                        <div>
                            <strong>{value}</strong>
                            <span>{change}</span>
                        </div>
                    </div>
                ))}
            </section>
            <button className="collapse-button" onClick={onToggleCollapsed} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}>
                <span>{collapsed ? "›" : "‹"}</span>
                <span className="collapse-label">Collapse</span>
            </button>
        </aside>
    );
}
