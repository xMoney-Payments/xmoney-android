# Changelog

All notable changes to the xMoney Android SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.0.2] - 2026-08-24

### Breaking

- `PaymentResult` is now `Complete` / `Failed` / `Canceled`. Cancel has no error payload. Failures use sanitized `PaymentError` messages.
- `SavedCard.issuerName` is now `bankName`, matching the cards API field.
- `WalletAppearance.style` and `borderType` are removed (`WalletButtonStyle`, `WalletBorderType` gone). Use `color`, `radius`, and `type`.

### Added

- `EmbeddedPaymentController.confirm()` for a merchant-owned Pay button
- `updateOrder()` on Element and Google Pay to replace the order on a mounted surface. Pay stays locked until `Ready`; gate a merchant CTA with `isInteractionEnabled`
- `updateAppearance()`, `updateStyle()`, `updateLocale()`, and `updateWalletAppearance()` on a mounted Element; `GooglePayController.updateAppearance()` for the wallet button
- `GooglePay.availability()` and `isAvailable` / `isReady` to gate the wallet without presenting UI
- Bulgarian, Hungarian, and Polish checkout copy (`bg-BG`, `hu-HU`, `pl-PL`)
- Edit / Done saved-card management with inline Remove / Keep it confirm

### Changed

- Default Pay button is a pill (`primaryButton.borderRadius` 9999); pass `12` for a squircle
- Default card validation is `onTouched`
- Payment Sheet always shows the SDK Pay button (`SubmitButtonConfig.visible` is Embedded-only)
- `paymentelement` no longer pulls in Google Pay — add the `googlepay` artifact if you need the wallet
- Pre-auth Google Pay cancel does not consume the order; present or tap again with the same intent
- 3DS challenge is a full-screen overlay (not a dialog window)

## [0.0.1] - 2026-08-12

First public release on Maven Central (`com.xmoney`).

Payment Sheet, Payment Element, and Google Pay. Artifacts: `payments-core`, `paymentsheet`, `paymentelement`, `googlepay`.
