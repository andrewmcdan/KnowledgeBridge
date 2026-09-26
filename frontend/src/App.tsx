import { useCallback, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { ContextColumn } from "./components/layout/ContextColumn";
import { PageHeading } from "./components/layout/PageHeading";
import { Sidebar } from "./components/layout/Sidebar";
import { Topbar } from "./components/layout/Topbar";
import { DetailDialog } from "./components/ui/DetailDialog";
import { navigation } from "./data/demoData";
import { AskAiPage } from "./features/ask-ai/AskAiPage";
import { EntitiesPage } from "./features/entities/EntitiesPage";
import { KnowledgeBasePage } from "./features/knowledge/KnowledgeBasePage";
import { PlaceholderPage } from "./features/overview/PlaceholderPage";
import { useGlobalShortcuts } from "./hooks/useGlobalShortcuts";
import { useHealthStatus } from "./hooks/useHealthStatus";
import type { Detail } from "./types/models";
import "./App.css";

function Workspace() {
    const navigate = useNavigate();
    const location = useLocation();
    const [collapsed, setCollapsed] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [search, setSearch] = useState("");
    const [saved, setSaved] = useState<string[]>([]);
    const [detail, setDetail] = useState<Detail | null>(null);
    const service = useHealthStatus();
    const closeMobileNavigation = useCallback(() => setMobileOpen(false), []);
    useGlobalShortcuts(closeMobileNavigation);

    const page = navigation.find(item => item.path === location.pathname)?.label ?? "Ask AI";
    const go = (path: string) => { navigate(path); setMobileOpen(false); };
    const toggleSaved = (title: string) => setSaved(current => current.includes(title) ? current.filter(item => item !== title) : [...current, title]);
    const toggleNavigation = () => {
        if (window.matchMedia("(max-width: 760px)").matches) setMobileOpen(value => !value);
        else setCollapsed(value => !value);
    };

    return <div className={`workspace ${collapsed ? "collapsed" : ""}`}>
        {mobileOpen && <button className="nav-backdrop" aria-label="Close navigation" onClick={closeMobileNavigation} />}
        <Sidebar collapsed={collapsed} mobileOpen={mobileOpen} onNavigate={closeMobileNavigation} onToggleCollapsed={() => setCollapsed(value => !value)} />
        <div className="main-shell">
            <Topbar search={search} onSearchChange={setSearch} onSearchSubmit={() => go("/knowledge")} onToggleNavigation={toggleNavigation} onOpenDetail={setDetail} />
            <div className="content-layout"><main className="main-content"><PageHeading title={page} />
                <Routes>
                    <Route path="/ask-ai" element={<AskAiPage search={search} saved={saved} onOpenDetail={setDetail} onToggleSaved={toggleSaved} />} />
                    <Route path="/knowledge" element={<KnowledgeBasePage search={search} saved={saved} onOpenDetail={setDetail} onToggleSaved={toggleSaved} />} />
                    <Route path="/entities" element={<EntitiesPage onOpenDetail={setDetail} />} />
                    <Route path="/dashboard" element={<PlaceholderPage page="Dashboard" service={service} onAskAi={() => go("/ask-ai")} />} />
                    <Route path="/uploads" element={<PlaceholderPage page="Uploads" service={service} onAskAi={() => go("/ask-ai")} />} />
                    <Route path="/usage" element={<PlaceholderPage page="Usage" service={service} onAskAi={() => go("/ask-ai")} />} />
                    <Route path="/admin" element={<PlaceholderPage page="Admin" service={service} onAskAi={() => go("/ask-ai")} />} />
                    <Route path="*" element={<Navigate to="/ask-ai" replace />} />
                </Routes>
                <div className="connection-status"><span className={service === "API & database connected" ? "connected" : ""} />{service}<span className="session-note">Sample data · changes stay in this session</span></div>
            </main><ContextColumn saved={saved} onOpenDetail={setDetail} onToggleSaved={toggleSaved} onNavigate={go} /></div>
        </div>
        {detail && <DetailDialog detail={detail} onClose={() => setDetail(null)} />}
    </div>;
}

function App() {
    return <BrowserRouter><Workspace /></BrowserRouter>;
}

export default App;
