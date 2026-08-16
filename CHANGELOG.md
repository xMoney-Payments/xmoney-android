# Changelog

All notable changes to the xMoney Android SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Edit / Done saved-card management with inline Remove / Keep it confirm

### Changed

- Gradle module `:embedded` renamed to `:paymentelement` (folder, project, and artifact now match)
- Shared `PaymentSession` owns bind generation, submit serialization, and consume rules (pre-auth Google Pay cancel does not consume)
- `paymentelement` no longer depends on `googlepay`; Play Wallet installs via `GooglePay.register()` / ContentProvider
- 3DS return URLs match scheme + host + path prefix (query-only shortcuts removed)
- Multipart fields reject CR/LF/boundary; API path IDs are percent-encoded
- `fromApiMap` parsers are internal; empty Google Pay tokens fail closed
- `dismiss()` never falls back to `Activity.finish()` when the close target is gone
- Cancellation during Google Pay host load is not reported as `LOAD_ERROR`
- DELETE saved-card requests fail closed on non-2xx (confirm stays open)
- Google Pay 3DS follows checkout-sdk `handlePaymentRedirect` (`pending-redirect` + `3d-pending` → `threeDSFlowUrl`)
- 3DS challenge is a full-screen overlay (not a dialog window)
- TEST Google Pay requests `PAN_ONLY` so the sheet returns an FPAN and the gateway can run 3DS (live still allows `CRYPTOGRAM_3DS`)

### Changed (previous)

- Google Pay fails closed when order amount or currency is missing (`0` amount still allowed)
- Embedded and standalone Google Pay commit bind only after load succeeds; overlapping binds are generation-guarded
- Payment Sheet / Google Pay `dismiss()` wait for an in-flight charge (idle close still cancels)
- Session-load coin is always xMoney `#7C4DFF` (not merchant `appearance.colors.primary`)
- Google Pay params load in parallel with site config and saved cards
- 3DS challenge URLs must be `https` at parse time

## [0.0.1] - 2026-08-12

First public release on Maven Central (`com.xmoney`).

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
