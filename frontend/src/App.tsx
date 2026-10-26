import { useCallback, useState } from "react";
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from "react-router-dom";
import { ContextColumn } from "./components/layout/ContextColumn";
import { PageHeading } from "./components/layout/PageHeading";
import { Sidebar } from "./components/layout/Sidebar";
import { Topbar } from "./components/layout/Topbar";
import { DetailDialog } from "./components/ui/DetailDialog";
import { navigation } from "./data/navigation";
import { AskAiPage } from "./features/ask-ai/AskAiPage";
import { AdminPage } from "./features/admin/AdminPage";
import { EntitiesPage } from "./features/entities/EntitiesPage";
import { KnowledgeBasePage } from "./features/knowledge/KnowledgeBasePage";
import { UploadsPage } from "./features/knowledge/UploadsPage";
import { UsagePage } from "./features/usage/UsagePage";
import { PlaceholderPage } from "./features/overview/PlaceholderPage";
import { LoginPage } from "./features/auth/LoginPage";
import { RegisterPage } from "./features/auth/RegisterPage";
import { AuthProvider } from "./features/auth/AuthProvider";
import { useAuth } from "./features/auth/useAuth";
import { useGlobalShortcuts } from "./hooks/useGlobalShortcuts";
import { useHealthStatus } from "./hooks/useHealthStatus";
import type { Detail } from "./types/models";
import "./App.css";

function Workspace() {
    const { signOut, user } = useAuth();
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

    const page = navigation.find((item) => item.path === location.pathname)?.label ?? "Ask AI";
    const go = (path: string) => {
        navigate(path);
        setMobileOpen(false);
    };
    const toggleSaved = (title: string) => setSaved((current) => (current.includes(title) ? current.filter((item) => item !== title) : [...current, title]));
    const toggleNavigation = () => {
        if (window.matchMedia("(max-width: 760px)").matches) setMobileOpen((value) => !value);
        else setCollapsed((value) => !value);
    };

    return (
        <div className={`workspace ${collapsed ? "collapsed" : ""}`}>
            {mobileOpen && <button className="nav-backdrop" aria-label="Close navigation" onClick={closeMobileNavigation} />}
            <Sidebar collapsed={collapsed} isAdmin={user?.role === "ADMIN"} mobileOpen={mobileOpen} service={service} onNavigate={closeMobileNavigation} onToggleCollapsed={() => setCollapsed((value) => !value)} />
            <div className="main-shell">
                {user && (
                    <Topbar
                        search={search}
                        onSearchChange={setSearch}
                        onSearchSubmit={() => go("/knowledge")}
                        onToggleNavigation={toggleNavigation}
                        onLogout={() => {
                            void signOut();
                            navigate("/", { replace: true });
                        }}
                        user={user}
                    />
                )}
                <div className="content-layout">
                    <main className="main-content">
                        <PageHeading title={page} />
                        <Routes>
                            <Route path="/ask-ai" element={<AskAiPage />} />
                            <Route path="/knowledge" element={<KnowledgeBasePage search={search} saved={saved} onOpenDetail={setDetail} onToggleSaved={toggleSaved} />} />
                            <Route path="/entities" element={<EntitiesPage onOpenDetail={setDetail} />} />
                            <Route path="/dashboard" element={<PlaceholderPage page="Dashboard" service={service} onAskAi={() => go("/ask-ai")} />} />
                            <Route path="/uploads" element={user?.role === "ADMIN" ? <UploadsPage /> : <Navigate to="/ask-ai" replace />} />
                            <Route path="/usage" element={<UsagePage />} />
                            <Route path="/admin" element={user?.role === "ADMIN" ? <AdminPage /> : <Navigate to="/ask-ai" replace />} />
                            <Route path="*" element={<Navigate to="/ask-ai" replace />} />
                        </Routes>
                    </main>
                    <ContextColumn onNavigate={go} />
                </div>
            </div>
            {detail && <DetailDialog key={detail.id ?? detail.title} detail={detail} onClose={() => setDetail(null)} />}
        </div>
    );
}

function AppRoutes() {
    const { status } = useAuth();

    if (status === "loading") return <main className="auth-loading">Restoring your session…</main>;

    return (
        <BrowserRouter>
            <Routes>
                {status === "authenticated" ? (
                    <>
                        <Route path="/" element={<Navigate to="/ask-ai" replace />} />
                        <Route path="/*" element={<Workspace />} />
                    </>
                ) : (
                    <>
                        <Route path="/register" element={<RegisterPage />} />
                        <Route path="*" element={<LoginPage />} />
                    </>
                )}
            </Routes>
        </BrowserRouter>
    );
}

function App() {
    return (
        <AuthProvider>
            <AppRoutes />
        </AuthProvider>
    );
}

export default App;
