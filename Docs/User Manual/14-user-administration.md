# 14. Administer user access

Existing administrators can grant admin access to an enabled standard user. The Admin screen is an access-granting workflow; it is not a complete account-management console.

## Find an account

1. Open **Admin** in the sidebar.
2. Under **User access**, use **Find a user** to filter by name or email.
3. Review the account's display name, email, role, and enabled status.
4. Use **Refresh users** if you need a new account list before acting.

No match can mean the person has not registered, the search is too narrow, or you need to refresh. Ask the person to register with the intended email rather than promoting a similarly named account.

## Grant admin access

1. Confirm the account identity and that the person is authorized to manage the workspace.
2. Select **Grant admin** for the enabled User account.
3. Read the **Grant admin access?** dialog. Check both the name and email.
4. Select **Confirm grant admin** to apply the change, or **Cancel** to leave the account unchanged. Escape cancels while the action is not busy.
5. Read the success message and tell the person to sign out and sign in again.

The person receives a new admin role in a newly issued token. Their current session does not automatically change role. A repeated promotion of an already promoted account does not create a second account or reset credentials. Disabled accounts cannot be promoted through this workflow.

## Understand the authority you are granting

Administrators can add knowledge, retry processing, edit manual notes, alter document lifecycle, permanently remove application knowledge, perform entity extraction, sync configured Jira projects, see workspace query usage, and grant admin access to other enabled users. There is no document-owner boundary limiting an admin to their own contributions.

Use an ordinary User account when you do not need those actions. Confirm the target identity before approving access; the current interface does not provide an undo/demotion action.

## Unsupported account tasks

The Admin screen does not create, disable, remove, or demote accounts; reset passwords; change emails; or edit profiles. Registration creates a standard User account from the sign-in screen. Contact the deployment operator for unsupported account changes.

Role and account changes do not revoke previously issued stateless tokens. They normally expire after eight hours; a new sign-in obtains new claims. Coordinate urgent access incidents with the operator rather than relying on a browser sign-out or database change to revoke every token.
