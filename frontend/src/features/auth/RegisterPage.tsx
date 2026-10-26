import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthPageLayout } from "./AuthPageLayout";
import { useAuth } from "./useAuth";
import { validateRegistration } from "./registration";

export function RegisterPage() {
    const { signUp } = useAuth();
    const navigate = useNavigate();
    const [displayName, setDisplayName] = useState("");
    const [email, setEmail] = useState("");
    const [password, setPassword] = useState("");
    const [confirmation, setConfirmation] = useState("");
    const [showPassword, setShowPassword] = useState(false);
    const [submitting, setSubmitting] = useState(false);
    const [error, setError] = useState<string | null>(null);

    const submit = async (event: FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (submitting) return;
        const validation = validateRegistration(displayName, email, password, confirmation);
        setError(validation);
        if (validation) return;
        setSubmitting(true);
        try {
            await signUp(displayName, email, password);
            navigate("/ask-ai", { replace: true });
        } catch (reason: unknown) {
            setError(reason instanceof Error ? reason.message : "We could not create your account. Please try again.");
        } finally {
            setSubmitting(false);
        }
    };

    return (
        <AuthPageLayout labelledBy="register-heading">
            <form className="login-form registration-form" onSubmit={(event) => void submit(event)}>
                <div className="login-form-heading">
                    <p className="login-eyebrow">GET STARTED</p>
                    <h1 id="register-heading">Create your account</h1>
                    <p>Join the workspace to explore shared knowledge and ask questions.</p>
                </div>
                <label htmlFor="register-name">Display name</label>
                <input id="register-name" name="displayName" autoComplete="name" required maxLength={255} disabled={submitting} value={displayName} onChange={(event) => setDisplayName(event.target.value)} />
                <label htmlFor="register-email">Email address</label>
                <input id="register-email" name="email" type="email" autoComplete="email" required maxLength={255} disabled={submitting} value={email} onChange={(event) => setEmail(event.target.value)} />
                <label htmlFor="register-password">Password</label>
                <div className="login-password-input">
                    <input
                        id="register-password"
                        name="password"
                        type={showPassword ? "text" : "password"}
                        autoComplete="new-password"
                        required
                        disabled={submitting}
                        aria-describedby="registration-password-help"
                        value={password}
                        onChange={(event) => setPassword(event.target.value)}
                    />
                    <button type="button" aria-label={showPassword ? "Hide passwords" : "Show passwords"} aria-pressed={showPassword} disabled={submitting} onClick={() => setShowPassword((value) => !value)}>
                        {showPassword ? "Hide" : "Show"}
                    </button>
                </div>
                <p id="registration-password-help" className="registration-help">
                    Use at least eight characters. No special character combinations are required.
                </p>
                <label htmlFor="register-confirmation">Confirm password</label>
                <input
                    id="register-confirmation"
                    name="passwordConfirmation"
                    type={showPassword ? "text" : "password"}
                    autoComplete="new-password"
                    required
                    disabled={submitting}
                    value={confirmation}
                    onChange={(event) => setConfirmation(event.target.value)}
                />
                {error && (
                    <p className="login-error" role="alert">
                        {error}
                    </p>
                )}
                <button className="login-submit" type="submit" disabled={submitting}>
                    {submitting ? "Creating account…" : "Create account"}
                </button>
                <p className="login-note">
                    Already have an account? <Link to="/">Sign in</Link>
                </p>
            </form>
        </AuthPageLayout>
    );
}
