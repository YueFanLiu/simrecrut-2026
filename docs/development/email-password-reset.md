# Email verified password recovery

The login screen now links to /forgot-password. Recovery requires a registered active account, a RuoYi CAPTCHA and a six-digit mailbox verification code. The existing authentication layout is retained.

## Gmail configuration

Place the shared private file named simrecrut-gmail.private.env on each teammate's Desktop. The backend reads its SMTP settings before creating the mail sender; these values override older SMTP values in the root .env. Database and other settings still come from the root .env or environment.

```text
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your-sender@gmail.com
SMTP_PASSWORD=your-google-app-password
```

Use a Google app password after enabling two-step verification, not the normal Google account password. SMTP_FROM is optional and defaults to SMTP_USERNAME. The sender must have permission to send as that address. Gmail uses authenticated STARTTLS with TLS required. Restart the backend after changing the file. The runtime must be able to connect to Google's SMTP server.

The sender's mailbox and application password must be supplied by its owner. Keep the private file outside the repository and share it only with authorized teammates. It is not embedded in frontend code, logs or documents. See [Google app-password instructions](https://support.google.com/mail/answer/185833).

The PowerShell launcher resolves the Windows Desktop folder, including redirected OneDrive desktops. Direct IDE or JAR launches default to user.home/Desktop; set SIMRECRUT_MAIL_FILE to the actual file path if that desktop is redirected. The loader accepts only the five SMTP settings and treats their values literally. If the desktop file is missing, existing environment/root .env settings remain available; without sender credentials, email verification stays disabled.

## API and recovery behavior

- GET /simrecrut/account/password/options exposes whether mail credentials are configured, without exposing their values. Configuration presence does not prove SMTP connectivity.
- POST /simrecrut/account/password/code accepts email, code and uuid. It returns a random requestId and the same generic success response for unregistered or inactive addresses. No email is sent to those addresses.
- POST /simrecrut/account/password/reset accepts email, requestId, emailCode, password and confirmation. It updates only the proved account's password, preserves all roles and permissions, and revokes that user's existing RuoYi login tokens.

Codes use SecureRandom, expire in 10 minutes and are consumed once. Redis holds a keyed digest of the code, a snapshot of the account's existing password hash and an attempt counter, never the plain code or new password. A snapshot check prevents a second outstanding code from overwriting a password changed after the request. Five incorrect attempts invalidate a challenge. Sending is limited to one request per address per 60 seconds, five per address per hour and ten per client IP per hour. Limits and code consumption use atomic Redis scripts. No code or reset secret is returned in the API response.

The code is consumed before updating the password. If the update fails, request another code. Session revocation failure after a successful update can produce a generic failure response; the new password may already be active. Session cleanup currently scans the existing RuoYi token keys; large deployments should maintain a per-user session index and stronger coordination with concurrent login.

This flow verifies mailbox possession for password recovery only. It does not add registration-time email verification or deliverability checks to existing accounts. Production privacy notices and sender reputation configuration remain separate work.

## Verification status

Java tests cover the mailbox proof, password hashing, invalid and reused codes, inactive/missing accounts, mail failure, configuration absence and targeted session revocation. An isolated test against the local Redis instance checks cooldown, expiry, five wrong attempts and single-use code consumption. Frontend transport tests verify public reset endpoints and the SMTP request timeout. No test sends real email or changes an existing user's password.

Real Gmail delivery and a real password change cannot be considered verified until the owner supplies a sender address and app password, receives a code, and personally enters the new password in the page.
