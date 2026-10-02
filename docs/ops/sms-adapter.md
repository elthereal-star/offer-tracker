# SMS Verification (Deferred)

The current account flow uses a phone number as an unverified login identifier. Registration and login require only a phone number and password; no SMS is sent. There is no password-reset or account-recovery flow yet.

Do not treat the stored phone number as proof of ownership. Before opening registration to the public, either add invitation controls or implement phone ownership verification and recovery with a selected SMS provider.

When SMS verification is implemented, keep the application vendor-neutral behind a small `SmsCodeSender` interface. The provider adapter must:

- read credentials only from the deployment secret manager or environment;
- use bounded timeouts and avoid blind retries for billable requests;
- send a normalized phone number and one-time code;
- avoid logging codes, full phone numbers, and provider credentials;
- enforce shared per-phone and per-IP rate limits;
- test successful delivery, invalid templates/signatures, provider timeouts, rate limits, duplicate requests, and secret rotation before production use.
