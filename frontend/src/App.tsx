import { useEffect, useRef, useState, type FormEvent } from "react";
import wordmark from "./assets/wordmark.png";
import "./App.css";

type IconName = "home" | "spark" | "book" | "users" | "upload" | "chart" | "shield" | "search" | "menu" | "send" | "file" | "chevron" | "copy" | "bookmark" | "up" | "down";
function Icon({ name, size = 20 }: { name: IconName; size?: number }) {
    const paths: Record<IconName, string> = {
        home: "M3 10 12 3l9 7v11h-6v-7H9v7H3Z", spark: "m12 3 2.5 6.5L21 12l-6.5 2.5L12 21l-2.5-6.5L3 12l6.5-2.5ZM20 2v4m-2-2h4",
        book: "M5 3h15v18H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Zm2 0v18M3 17h17", users: "M16 21v-3a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v3m20 0v-3a4 4 0 0 0-3-4M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm9 1a3 3 0 0 1 0 6",
        upload: "M12 16V3m-5 5 5-5 5 5M3 14v6a1 1 0 0 0 1 1h16a1 1 0 0 0 1-1v-6", chart: "M3 13h4v8H3Zm7-6h4v14h-4Zm7-5h4v19h-4Z", shield: "m12 3 9 4v5c0 5-9 10-9 10S3 17 3 12V7Zm-4 9 3 3 5-6", search: "M10 3a7 7 0 1 0 0 14 7 7 0 0 0 0-14Zm5 12 6 6", menu: "M4 6h16M4 12h16M4 18h16", send: "m3 10 18-7-7 18-3-8Zm8 3L21 3", file: "M5 3h9l5 5v13H5Zm9 0v6h5M8 13h8m-8 4h8", chevron: "m9 5 7 7-7 7", copy: "M9 9h12v12H9ZM15 9V3H3v12h6", bookmark: "M6 3h12v18l-6-4-6 4Z", up: "M7 21H3V10h4Zm0-11 5-7h2v7h6l1 2-3 9H7", down: "M7 3H3v11h4Zm0 11 5 7h2v-7h6l1-2-3-9H7",
    };
    return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true"><path d={paths[name]} /></svg>;
}

const documents = [
    { title: "Procurement Policy v2.1", type: "Policy", date: "Apr 12, 2024", color: "blue", text: "Software purchases over $5,000 require a purchase request, budget validation, manager approval, and Procurement review. A purchase order must be issued before ordering." },
    { title: "Finance Approval Matrix", type: "Spreadsheet", date: "Mar 28, 2024", color: "green", text: "Finance reviews available budget and cost appropriateness. Purchases over $20,000 also require final approval from the CFO or delegated authority." },
    { title: "IT Purchasing Guidelines", type: "Guideline", date: "Feb 15, 2024", color: "purple", text: "IT Operations reviews software for security, licensing, and integration requirements before Procurement finalizes vendor terms." },
    { title: "Software Expense Policy", type: "Policy", date: "Jan 10, 2024", color: "blue", text: "Software costs are recorded against the Software Budget cost center. Include the business justification, vendor details, and total cost with each request." },
    { title: "Vendor Management Policy", type: "Policy", date: "Nov 5, 2023", color: "blue", text: "Procurement reviews vendor terms and contract requirements. Approved orders are sent to the vendor by the Procurement team." },
];
const entities = ["Finance Team", "Procurement", "IT Operations", "Software Budget"];
const steps = [
    ["Request Initiation", "The requester submits a purchase request in the Procurement system, with business justification, vendor details, and total cost."],
    ["Budget Check", "Finance validates that sufficient budget exists in the Software Budget category."],
    ["Manager Approval", "The requester's manager approves the request."],
    ["Finance Review", "The Finance Team reviews the request for policy compliance and cost appropriateness."],
    ["IT Operations Approval", "IT Operations verifies the software meets security, licensing, and integration requirements."],
    ["Procurement Approval", "Procurement reviews vendor terms and contract requirements."],
    ["Final Approval", "The CFO or delegated authority provides final approval for purchases over $20,000."],
    ["Purchase Order Issued", "Upon all approvals, Procurement issues the purchase order to the vendor."],
];
const sampleQuestion = "What is the expense approval process for software purchases over $5,000?";
const navigation: { label: string; icon: IconName }[] = [{ label: "Dashboard", icon: "home" }, { label: "Ask AI", icon: "spark" }, { label: "Knowledge Base", icon: "book" }, { label: "Entities", icon: "users" }, { label: "Uploads", icon: "upload" }, { label: "Usage", icon: "chart" }, { label: "Admin", icon: "shield" }];

function DetailDialog({ detail, onClose }: { detail: { title: string; text: string }; onClose: () => void }) {
    const dialog = useRef<HTMLDialogElement>(null);
    useEffect(() => { dialog.current?.showModal(); }, []);
    return <dialog ref={dialog} aria-labelledby="detail-title" className="detail-modal" onClose={onClose} onClick={event => { if (event.target === event.currentTarget) dialog.current?.close(); }}>
        <span className="preview-badge">Sample content</span><h2 id="detail-title">{detail.title}</h2><p>{detail.text}</p>
        <button className="primary-button" autoFocus onClick={() => dialog.current?.close()}>Close</button>
    </dialog>;
}

function App() {
    const [page, setPage] = useState("Ask AI");
    const [collapsed, setCollapsed] = useState(false);
    const [mobileOpen, setMobileOpen] = useState(false);
    const [mode, setMode] = useState("Synthesis");
    const [question, setQuestion] = useState(sampleQuestion);
    const [search, setSearch] = useState("");
    const [notice, setNotice] = useState("");
    const [expanded, setExpanded] = useState(false);
    const [saved, setSaved] = useState<string[]>([]);
    const [feedback, setFeedback] = useState("");
    const [detail, setDetail] = useState<{ title: string; text: string } | null>(null);
    const [service, setService] = useState("Checking connection…");
    useEffect(() => {
        const controller = new AbortController();
        void fetch("/api/health", { signal: controller.signal }).then(async response => {
            if (!response.ok) throw new Error();
            const data = await response.json();
            setService(data.status === "UP" && data.database === "UP" ? "API & database connected" : "Platform needs attention");
        }).catch(() => { if (!controller.signal.aborted) setService("Backend unavailable"); });
        return () => controller.abort();
    }, []);
    useEffect(() => {
        const onKey = (event: KeyboardEvent) => {
            if ((event.ctrlKey || event.metaKey) && event.key === "k") { event.preventDefault(); document.getElementById("global-search")?.focus(); }
            if (event.key === "Escape") setMobileOpen(false);
        };
        window.addEventListener("keydown", onKey);
        return () => window.removeEventListener("keydown", onKey);
    }, []);
    const go = (name: string) => { setPage(name); setMobileOpen(false); setNotice(""); };
    const filtered = documents.filter(doc => `${doc.title} ${doc.type} ${doc.text}`.toLowerCase().includes(search.toLowerCase()));
    const submit = (event: FormEvent) => {
        event.preventDefault();
        setNotice(question.trim() === sampleQuestion ? "Showing the sample response below. Live AI queries are not connected yet." : "Live AI queries are not connected yet. The answer below is the labeled sample, not a response to your question.");
    };
    const documentRow = (doc: typeof documents[number]) => <div className="document-row" key={doc.title}>
        <button className="document-open" onClick={() => setDetail({ title: doc.title, text: doc.text })}><span className={`file-icon ${doc.color}`}><Icon name="file" size={18} /></span><span><strong>{doc.title}</strong><small>{doc.type} <span>·</span> {doc.date}</small></span></button>
        <button className={`icon-button bookmark ${saved.includes(doc.title) ? "saved" : ""}`} aria-label={`${saved.includes(doc.title) ? "Unsave" : "Save"} ${doc.title}`} aria-pressed={saved.includes(doc.title)} onClick={() => setSaved(saved.includes(doc.title) ? saved.filter(title => title !== doc.title) : [...saved, doc.title])}><Icon name="bookmark" size={17} /></button>
    </div>;
    return <div className={`workspace ${collapsed ? "collapsed" : ""}`}>
        {mobileOpen && <button className="nav-backdrop" aria-label="Close navigation" onClick={() => setMobileOpen(false)} />}
        <aside className={`sidebar ${mobileOpen ? "mobile-open" : ""}`}>
            <a className="brand" href="#ask-ai" aria-label="KnowledgeBridge home" onClick={() => go("Ask AI")}><img className="wordmark" src={wordmark} alt="KnowledgeBridge" /><img className="compact-logo" src="/logo.png" alt="" /></a>
            <nav aria-label="Main navigation">{navigation.map(item => <button key={item.label} className={`nav-item ${page === item.label ? "active" : ""}`} onClick={() => go(item.label)} aria-current={page === item.label ? "page" : undefined} title={item.label}><Icon name={item.icon} /><span>{item.label}</span>{item.label === "Admin" && <span className="nav-chevron">⌄</span>}</button>)}</nav>
            <section className="summary panel"><h2><Icon name="chart" size={17} /> Today's Summary <span className="demo-label">Demo</span></h2>{[["Knowledge Items", "12,842", "↑ 3.2%"], ["Queries Today", "248", "↑ 18.6%"], ["Estimated AI Cost", "$1.48", "↓ 6.1%"], ["Cache Hit Rate", "82%", "↑ 5.4%"]].map(([label, value, change]) => <div className="metric" key={label}><small>{label}</small><div><strong>{value}</strong><span>{change}</span></div></div>)}</section>
            <button className="collapse-button" onClick={() => setCollapsed(!collapsed)} aria-label={collapsed ? "Expand sidebar" : "Collapse sidebar"}><span>{collapsed ? "›" : "‹"}</span><span className="collapse-label">Collapse</span></button>
        </aside>
        <div className="main-shell">
            <header className="topbar"><button className="icon-button menu-button" aria-label="Toggle navigation" onClick={() => { if (window.matchMedia("(max-width: 760px)").matches) setMobileOpen(!mobileOpen); else setCollapsed(!collapsed); }}><Icon name="menu" /></button>
                <form className="global-search" onSubmit={event => { event.preventDefault(); go("Knowledge Base"); }}><Icon name="search" size={18} /><input id="global-search" aria-label="Search sample knowledge base" placeholder="Search knowledge base, documents, entities…" value={search} onChange={event => setSearch(event.target.value)} /><kbd>⌘ K</kbd></form>
                <button className="organization" onClick={() => setDetail({ title: "Acme Corporation", text: "Acme Corporation is the fictional organization used in this interface preview. Organization switching will be available when accounts are connected." })}><Icon name="home" size={18} /><span>Acme Corporation</span><span>⌄</span></button>
                <button className="profile" onClick={() => setDetail({ title: "Demo workspace", text: "You are viewing the frontend scaffold. Login, user profiles, and role-based access are not connected yet." })}><span className="avatar">AC</span><span>Demo user</span><span>⌄</span></button>
            </header>
            <div className="content-layout"><main className="main-content">
                <div className="page-heading"><div><h1>{page}</h1><p>{page === "Ask AI" ? "Get instant answers from your company knowledge." : `Explore your company ${page.toLowerCase()}.`}</p></div><span className="preview-badge">Demo workspace</span></div>
                {page === "Ask AI" ? <>
                    <form className="question-panel panel" onSubmit={submit}><label htmlFor="question">Your question</label><div className="question-input"><textarea id="question" value={question} onChange={event => setQuestion(event.target.value)} /><button className="send-button" aria-label="Submit question" disabled={!question.trim()}><Icon name="send" size={18} /></button></div><fieldset><legend>Mode <span title="Search returns documents; Synthesis combines them into an answer.">ⓘ</span></legend><div className="mode-options">{["Search", "Synthesis"].map(value => <label className={`mode-option ${mode === value ? "selected" : ""}`} key={value}><Icon name={value === "Search" ? "search" : "spark"} size={18} /><span>{value}</span><input type="radio" name="mode" value={value} checked={mode === value} onChange={() => setMode(value)} /></label>)}</div></fieldset>{notice && <p className="notice" role="status">{notice}</p>}</form>
                    <section className="answer-panel panel"><div className="answer-body"><div className="section-heading"><h2><Icon name={mode === "Synthesis" ? "spark" : "search"} />{mode === "Synthesis" ? "AI Answer" : "Search Results"}</h2><span className="grounding"><Icon name="shield" size={17} /> Sample content · live AI not connected</span></div>
                        {mode === "Synthesis" ? <><p>For software purchases over $5,000, Acme Corporation requires a multi-step approval process to ensure financial oversight, vendor compliance, and alignment with IT standards.</p><ol className="approval-steps">{steps.map(([title, text]) => <li key={title}><strong>{title}:</strong> {text}</li>)}</ol><h3 className="citation-heading">Citations ({expanded ? 5 : 3})</h3><div className="citations">{documents.slice(0, expanded ? 5 : 3).map((doc, index) => <button className="citation" key={doc.title} onClick={() => setDetail({ title: doc.title, text: doc.text })}><span className={`file-icon ${doc.color}`}><Icon name="file" size={23} /></span><span><strong>{doc.title}</strong><small>{doc.type}</small><small className="updated">Updated {doc.date}</small></span><span className="citation-number">{index + 1}</span></button>)}</div><button className="text-button" onClick={() => setExpanded(!expanded)}>{expanded ? "Show fewer citations" : "View all citations"}<span>{expanded ? "⌃" : "⌄"}</span></button></> : <div className="search-results">{filtered.length ? filtered.map(documentRow) : <p>No sample documents match your search.</p>}</div>}
                    </div>{mode === "Synthesis" && <footer className="answer-footer"><span>{feedback ? "Thanks — feedback recorded for this session." : "Was this answer helpful?"}</span><button className="icon-button" aria-label="Helpful" aria-pressed={feedback === "yes"} onClick={() => setFeedback("yes")}><Icon name="up" size={17} /></button><button className="icon-button" aria-label="Not helpful" aria-pressed={feedback === "no"} onClick={() => setFeedback("no")}><Icon name="down" size={17} /></button><button className="copy-button" onClick={() => { void navigator.clipboard.writeText(`Sample answer\n${steps.map(([title, text], index) => `${index + 1}. ${title}: ${text}`).join("\n")}`).then(() => setNotice("Sample answer copied.")).catch(() => setNotice("Clipboard access is unavailable. Select the answer text to copy it.")); }}>Copy <Icon name="copy" size={18} /></button></footer>}</section>
                </> : <section className="collection panel"><h2>{page === "Knowledge Base" ? "Sample documents" : page === "Dashboard" ? "Workspace overview" : page}</h2>{page === "Knowledge Base" ? <>{filtered.map(documentRow)}{!filtered.length && <p>No sample documents match “{search}”.</p>}</> : page === "Entities" ? entities.map((entity, index) => <button className="entity-row" key={entity} onClick={() => setDetail({ title: entity, text: `${entity} is mentioned in the sample purchasing workflow. Entity extraction is not connected yet.` })}><span className={`entity-icon color-${index}`}><Icon name="users" /></span><span><strong>{entity}</strong><small>{index === 3 ? "Cost Center" : "Department"}</small></span><Icon name="chevron" size={16} /></button>) : <><p>{page === "Dashboard" ? service : page === "Uploads" ? "Document upload and ingestion are not connected yet. The queue at right shows sample statuses." : page === "Usage" ? "The sidebar shows sample metrics. Live usage tracking and cost estimates are not connected yet." : "Account management and role settings will appear here once authentication is connected."}</p><button className="primary-button" onClick={() => go("Ask AI")}>Explore Ask AI</button></>}</section>}
                <div className="connection-status"><span className={service === "API & database connected" ? "connected" : ""} />{service}<span className="session-note">Sample data · changes stay in this session</span></div>
            </main><aside className="context-column" aria-label="Supporting knowledge">
                <section className="panel context-panel"><h2><Icon name="users" size={18} /> Detected Entities</h2>{entities.map((entity, index) => <button key={entity} className="entity-row" onClick={() => setDetail({ title: entity, text: `${entity} is part of the sample purchasing workflow. ${index === 3 ? "This cost center tracks software spending." : "This department reviews requests within its area of responsibility."}` })}><span className={`entity-icon color-${index}`}><Icon name={index === 3 ? "file" : "users"} size={19} /></span><span><strong>{entity}</strong><small>{index === 3 ? "Cost Center" : "Department"}</small></span></button>)}<button className="panel-link" onClick={() => go("Entities")}>View all entities <Icon name="chevron" size={16} /></button></section>
                <section className="panel context-panel"><h2><Icon name="file" size={18} /> Related Documents</h2>{documents.map(documentRow)}<button className="panel-link" onClick={() => { setSearch(""); go("Knowledge Base"); }}>View all documents <Icon name="chevron" size={16} /></button></section>
                <section className="panel context-panel upload-panel"><h2><Icon name="upload" size={18} /> Upload / Ingestion Status <button className="text-button" onClick={() => go("Uploads")}>View all</button></h2>{[["Q2 Vendor Contracts.md", "Pending"], ["IT Security Standards.md", "Processing"], ["Finance Q1 Reports.md", "Completed"], ["Legacy HR Guidelines.md", "Failed"]].map(([file, state]) => <div className="upload-row" key={file}><Icon name="file" size={13} /><span>{file}</span><strong className={state.toLowerCase()}>{state}</strong></div>)}</section>
            </aside></div>
        </div>
        {detail && <DetailDialog detail={detail} onClose={() => setDetail(null)} />}
    </div>;
}
export default App;
