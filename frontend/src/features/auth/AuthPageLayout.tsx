import type { ReactNode } from "react";
import wordmark from "../../assets/wordmark.png";

export function AuthPageLayout({ children, labelledBy }: { children: ReactNode; labelledBy: string }) {
    return (
        <main className="login-page">
            <section className="login-introduction" aria-labelledby="login-introduction-heading">
                <a className="login-brand" href="/" aria-label="KnowledgeBridge home">
                    <img src={wordmark} alt="KnowledgeBridge" />
                </a>
                <div className="login-introduction-content">
                    <p className="login-eyebrow">CONNECTED KNOWLEDGE, CLEAR ANSWERS</p>
                    <h1 id="login-introduction-heading">Make every answer easier to trust.</h1>
                    <p>KnowledgeBridge brings your organization&apos;s documents, people, and insights together in one searchable workspace.</p>
                    <div className="login-feature-list">
                        <div>
                            <span className="login-feature-icon">01</span>
                            <p>
                                <strong>Find trusted context</strong>
                                Search across the knowledge your team already relies on.
                            </p>
                        </div>
                        <div>
                            <span className="login-feature-icon">02</span>
                            <p>
                                <strong>Get grounded answers</strong>
                                Explore clear answers with links back to their sources.
                            </p>
                        </div>
                    </div>
                </div>
                <p className="login-copyright">(c) 2026 KnowledgeBridge</p>
            </section>

            <section className="login-form-section" aria-labelledby={labelledBy}>
                {children}
            </section>
        </main>
    );
}
