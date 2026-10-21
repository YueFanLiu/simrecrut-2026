# Frontend register

`/register` shares the responsive login layout and password visibility controls.
Connected registration supports Candidate and HR accounts, matching the Java API.
The form validates a 3-20 character username, an email up to 254 characters, a
15-128 Unicode code-point password, password confirmation and notice acknowledgement.
It offers no Admin or Researcher registration option.

Send code calls `POST /api/v1/auth/registration/code` with the normalized email.
The response supplies `challengeId`, `expiresInSeconds` and `resendAfterSeconds`.
The form displays a resend countdown, requires a six-digit code and invalidates
the challenge when the email changes or the code expires. A 429 Retry-After
header delays another send. Code sending does not submit the registration form.

Create account calls `POST /api/v1/auth/register` with only `username`, `email`,
`password`, `passwordConfirmation`, `accountType`, `challengeId`, `emailCode` and
`agreedToTerms`. Duplicate, verification and availability failures stay on the form.
A failed submission requires a new verification code because the backend may have
consumed the challenge before account creation. The success page shows the returned
username and account type and links back to login without automatically signing in.
The form clears passwords and verification data after success.

The notices explain persistence and email verification, while identifying the
terms as provisional project notices. Demo mode retains the existing temporary
Candidate-only registration and its original 8-20 character password rules.
No demo credentials are used to authorize real requests.

Use `npm run dev:connected` as described in [Local setup](local-setup.md).
Node contract and validation tests cover API fields, Unicode password boundaries
and email limits. Intercepted browser checks cover code sending, changing email,
HR registration, success, login and narrow-screen layout. Actual SMTP delivery and
account creation require a configured Java backend and are not verified by mocks.
