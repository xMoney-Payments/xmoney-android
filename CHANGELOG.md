# Changelog

All notable changes to the xMoney Android SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- Public API rename: packages `com.xmoney.payments` / `paymentsheet` / `paymentelement` / `googlepay`; artifacts `payments-core`, `paymentsheet`, `paymentelement`, `googlepay`
- `PaymentConfig` + swap-safe `PaymentIntent(OrderCredentials)`; session tokens fetched only by the SDK
- Entry points: `PaymentSheet`, `PaymentElement`, `GooglePay` (no `XMoney*` / Checkout prefixes)
- Merchant-facing `PaymentResult.errorMessage` uses stable SDK strings (no raw server error bodies)
- Payment Sheet / Google Pay prefer in-memory request-scoped config; Intent extras are process-death fallback

### Added

- Initial `core` and `paymentsheet` module split
- Compose payment sheet with Google Pay, saved cards, and 3DS
- Contract test harness driven by `test-vectors.json`
- In-repo `example` Compose app
- CI workflow for unit tests and example assemble
- Request-scoped `GooglePaySessionRegistry` (concurrent `present` safe)
- TLS pinning risk acceptance documented in `SECURITY.md`
