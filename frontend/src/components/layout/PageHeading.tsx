export function PageHeading({ title }: { title: string }) {
    return (
        <div className="page-heading">
            <div>
                <h1>{title}</h1>
                <p>{title === "Ask AI" ? "Get instant answers from your company knowledge." : title === "Admin" ? "Manage workspace access and administrator roles." : `Explore your company ${title.toLowerCase()}.`}</p>
            </div>
        </div>
    );
}
