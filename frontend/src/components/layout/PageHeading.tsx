export function PageHeading({ title }: { title: string }) {
    return <div className="page-heading"><div><h1>{title}</h1><p>{title === "Ask AI" ? "Get instant answers from your company knowledge." : `Explore your company ${title.toLowerCase()}.`}</p></div><span className="preview-badge">Demo workspace</span></div>;
}
