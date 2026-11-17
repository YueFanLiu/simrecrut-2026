# Candidate account registration

The existing login and registration screens now use persistent RuoYi accounts in connected mode. Explicit demo mode continues to use temporary in-memory accounts.

## Endpoints

- GET /simrecrut/account/options returns whether registration is enabled and exactly one active Candidate role exists.
- POST /simrecrut/account/register accepts username, email, password, confirmation, agreedToTerms, code and uuid.
- POST /simrecrut/account/login accepts username (a username or email), password, code and uuid. It delegates authentication, account status, password lockout and tokens to RuoYi.
- The existing /captchaImage, /getInfo and /logout endpoints remain unchanged.

## Account storage and permissions

Registration uses RuoYi's transactional insertUser operation to store the account in sys_user and its server-selected Candidate role in sys_user_role. It never accepts a role or permission from the request. Passwords use RuoYi BCrypt. Both username and email must be unique according to the existing RuoYi checks. Registrations are serialized within the application; a multi-instance deployment still needs database uniqueness constraints for concurrent registration.

Use 3-20 letters, numbers or underscores for the username and 8-20 password characters including letters and numbers. The password maximum follows RuoYi's login limit. Email addresses are not verified and no email is sent. The registration and privacy dialogs are development notices, not finalized service terms.

Initialize the Candidate role and resume permissions using backend/java/src/main/resources/db/ruoyi/simrecrut-candidate-role.sql after the RuoYi schema and business menus are installed. Missing or inactive roles keep the public registration option unavailable. Administrator, HR and Researcher privileges are not granted by registration.

Set SIMRECRUT_REGISTRATION_ENABLED=false to close public registration. The configured RuoYi CAPTCHA policy still applies; this adapter does not disable or bypass it.

## Verification

Automated service tests cover hashing, the Candidate role, duplicate identifiers, validation, CAPTCHA failure, email resolution, disabled registration and missing role configuration. Frontend registration tests cover matching validation and demo isolation. A live request with an intentionally invalid CAPTCHA was rejected without creating an account.

Successful real registration and login require the user to enter a new password and complete CAPTCHA. Until those steps are completed, do not describe the real browser success flow as tested. Existing reports dated 2026-10-12 describe the state before this integration.

The user has since confirmed successful real registration and login. Mailbox-based password recovery is documented in [Email verified password recovery](email-password-reset.md); its Gmail delivery test still requires sender credentials.
