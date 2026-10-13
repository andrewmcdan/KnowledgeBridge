import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import wordmark from "../../assets/wordmark.png";
import { useAuth } from "./useAuth";

export function LoginPage() {
    const navigate = useNavigate();
    const { signIn } = useAuth();
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [remember, setRemember] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const [submitting, setSubmitting] = useState(false);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        setError(null);
        setSubmitting(true);

        try {
            await signIn(email, password, remember);
            navigate("/ask-ai", { replace: true });
        } catch (signInError) {
            setError(signInError instanceof Error ? signInError.message : "We could not sign you in. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

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

            <section className="login-form-section" aria-labelledby="sign-in-heading">
                <form className="login-form" onSubmit={submit}>
                    <div className="login-form-heading">
                        <p className="login-eyebrow">WELCOME BACK</p>
                        <h1 id="sign-in-heading">Sign in to KnowledgeBridge</h1>
                        <p>Enter your details to access your workspace.</p>
                    </div>

                    <label htmlFor="email">Email address</label>
                    <input id="email" name="email" type="email" autoComplete="email" placeholder="you@company.com" value={email} onChange={(event) => setEmail(event.target.value)} required />

                    <div className="login-password-label">
                        <label htmlFor="password">Password</label>
                        <button type="button">Forgot password?</button>
                    </div>
                    <div className="login-password-input">
                        <input
                            id="password"
                            name="password"
                            type={showPassword ? "text" : "password"}
                            autoComplete="current-password"
                            placeholder="Enter your password"
                            value={password}
                            onChange={(event) => setPassword(event.target.value)}
                            required
                        />
                        <button type="button" onClick={() => setShowPassword((visible) => !visible)} aria-label={showPassword ? "Hide password" : "Show password"}>
                            {showPassword ? "Hide" : "Show"}
                        </button>
                    </div>

                    <label className="login-remember">
                        <input type="checkbox" name="remember" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
                        <span>Remember me on this device</span>
                    </label>

                    {error && (
                        <p className="login-error" role="alert">
                            {error}
                        </p>
                    )}

                    <button className="login-submit" type="submit" disabled={submitting}>
                        {submitting ? "Signing in…" : "Sign in"}
                    </button>

                    <p className="login-note">Use your KnowledgeBridge account to access the workspace.</p>
                </form>
            </section>
        </main>
    );
}
