# Cloud SMS Adapter Contract

The application deliberately does not bind to an SMS vendor. Registration depends on the small `SmsCodeSender` interface:

```java
void send(String phone, String code);
```

Add one implementation in the deployment module or a private integration module and activate it with the `sms-cloud` profile. The implementation must be the only `SmsCodeSender` bean in that profile. It should:

- read credentials from the deployment secret manager or environment, never from source, exports, logs, or frontend assets;
- use bounded connect and read timeouts and no blind retries for billable requests;
- pass the normalized E.164 phone number and the generated code to the provider template;
- avoid logging the code and full phone number, and redact provider responses;
- map provider rejection/rate-limit/unavailability to a stable `BusinessException` response;
- expose provider request IDs only in correlation-safe structured logs, without credentials or message content.

When `production` is active without a cloud adapter, the built-in fallback keeps the application safe and returns HTTP 503 for verification-code sends. This is intentional: it prevents accidental use of the development logging sender. The `sms-cloud` profile must only be activated together with a concrete `SmsCodeSender` implementation; otherwise startup fails fast instead of exposing a partially configured registration flow.

Before go-live, test successful delivery, invalid template/signature, provider timeout, provider rate limiting, duplicate requests, and secret rotation in a non-production account.
