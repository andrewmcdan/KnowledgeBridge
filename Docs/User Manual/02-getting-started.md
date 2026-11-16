# 2. Getting started

## Before you begin

Obtain the KnowledgeBridge web address from your administrator and use a current desktop browser. The administrator must have started the application and its required services. A working page does not by itself mean AI search, transcription, or Jira is configured.

For a local demonstration, the usual address is `http://localhost:3000`. A developer running the host launcher usually uses `http://localhost:5173`. These addresses refer to the computer running the application; ask for the correct deployment address when working remotely. Folder selection and the 3D graph depend on browser support; alternatives are described in their sections.

## Create an account

1. Open the web address and select **Create account** on the sign-in page.
2. Enter your **Display name** and **Email address**.
3. Enter a password, then enter exactly the same value in **Confirm password**. Use **Show** if you need to check the entries on a private screen.
4. Select **Create account**. On success, KnowledgeBridge signs you in and opens Ask AI.

A new account is always a standard User account. There is no email-verification step in this version. Registration uses session storage rather than Remember Me. Ask an existing administrator to grant admin access if your responsibilities require it.

Names and emails must be nonblank and no more than 255 characters. Emails are trimmed and matched without case differences. Passwords are preserved exactly, including spaces. Passwords require at least eight Unicode characters and must fit the backend's 72-byte UTF-8 limit; some non-ASCII characters use more than one byte. If the email is already registered, use **Sign in** rather than trying to create another account.

## Sign in

1. Enter your email address and password.
2. Optionally select **Remember me on this device** on a device you trust.
3. Select **Sign in**. Ask AI opens after authentication succeeds.

Remember Me keeps the token in persistent browser storage. Without it, the token is kept in session storage for the browser tab/session. Remember Me does not extend token expiry. Tokens last eight hours by default, and the application does not silently refresh them.

The sample local accounts are `admin@acme.example` / `admin123` and `user@acme.example` / `user123`. They belong to the project's synthetic development data. Use the account details supplied for your actual deployment, not these demonstration credentials.

## Expired sessions and changed roles

When a protected request returns an authentication failure, the application clears that rejected session and returns you to sign in with an expiry message. Sign in again and repeat the action if appropriate. Text already typed or results shown on a page should not be treated as saved work.

If an administrator grants you admin access, sign out and sign in again. Existing tokens retain their original role until replaced or expired. Refreshing the page alone does not obtain new role claims.

The **Forgot password?** control is a placeholder in this version. There is no working self-service password reset, profile-editing flow, email-change screen, account-disable action, or admin-demotion action. Contact the deployment administrator for an account problem.

## Sign out

Open the profile menu at the top right and select **Sign out**. Local credentials are cleared even if the server cannot acknowledge the request. Sign out on shared devices and avoid saving passwords there.

Sign-out removes the browser's token; it does not revoke a copied token on the server. The server's tokens remain valid until expiry. Report suspected credential exposure to the administrator rather than assuming sign-out invalidates every copy.
