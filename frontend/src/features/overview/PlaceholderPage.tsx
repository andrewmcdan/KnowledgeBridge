interface PlaceholderPageProps {
    page: "Dashboard" | "Uploads" | "Usage" | "Admin";
    service: string;
    onAskAi: () => void;
}

const descriptions: Record<PlaceholderPageProps["page"], (service: string) => string> = {
    Dashboard: (service) => service,
    Uploads: () => "Use the Uploads page to add Markdown files or notes. Ingestion will be connected in a future update.",
    Usage: () => "Live usage tracking and cost estimates will appear here when connected.",
    Admin: () => "Account management and role settings will appear here once authentication is connected.",
};

export function PlaceholderPage({ page, service, onAskAi }: PlaceholderPageProps) {
    return (
        <section className="collection panel">
            <h2>{page === "Dashboard" ? "Workspace overview" : page}</h2>
            <p>{descriptions[page](service)}</p>
            <button className="primary-button" onClick={onAskAi}>
                Explore Ask AI
            </button>
        </section>
    );
}
