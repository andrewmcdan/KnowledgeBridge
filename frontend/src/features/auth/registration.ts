export function validateRegistration(displayName: string, email: string, password: string, confirmation: string): string | null {
    if (!displayName.trim()) return "Enter your name.";
    if (displayName.trim().length > 255) return "Your name must not exceed 255 characters.";
    if (!email.trim()) return "Enter your email address.";
    if (email.trim().length > 255) return "Your email address must not exceed 255 characters.";
    if (Array.from(password).length < 8) return "Use at least eight characters for your password.";
    if (new TextEncoder().encode(password).byteLength > 72) return "Your password is too long. Use fewer characters (maximum 72 UTF-8 bytes).";
    if (password !== confirmation) return "The passwords do not match.";
    return null;
}
