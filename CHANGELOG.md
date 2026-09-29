# Changelog

All notable changes to the xMoney Android SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- `Ready` on Payment Element, Payment Sheet, and the Compose Google Pay button is emitted after the card form has been laid out and, when Google Pay is offered, the wallet button has pre-drawn. It no longer fires when order bind returns.
- Payment Element and Payment Sheet keep a loading coin over the form until that first draw. The cover swallows taps. A later `updateOrder` emits `Ready` again and does not bring the coin back.
- The loading coin stays at its initial height. The surface resizes once, when the form and Google Pay button have drawn.
- Compose `GooglePayEvent.Ready` fires after the button has pre-drawn, or as soon as Google Pay is known to be unavailable. Activity `GooglePay.present()` / `updateOrder()` still emit `Ready` when the order is bound on the open host.
- `EmbeddedPaymentController.updateOrder()` no longer emits `Ready`. `PaymentElement` does, after the surface draws.

### Fixed

- A merchant loader that collapsed the checkout to zero height could open before the Google Pay button had a real size. The example gate now lays the child out at its intrinsic size while covered, so the button can draw before `Ready`.

## [1.0.0] - 2026-09-08

First stable release. API unchanged since 0.0.3.

## [0.0.3] - 2026-09-02

### Added

- `GooglePay.updateOrder()` rebinds a new `PaymentIntent` on the open Activity host without dismissing it. The wallet button stays locked until `Ready`.

### Changed

- Idle `PaymentSheet.present()` dismisses the previous host (`Canceled`) then presents; a second present no-ops while a charge is in flight.
- `GooglePay.present()` no-ops while this instance has an open host. Use `updateOrder()` or `dismiss()` then `present()`.
- Payment Element no longer applies outer content insets or a page fill; merchants and Payment Sheet own page spacing and surface color.
- `appearance.borderRadius` / `borderWidth` now apply to card fields and the methods container (defaults 16 / 20); field chrome uses `componentBorder` / `error`.

### Fixed

- TalkBack labels on condensed card fields, the sheet close button, and the 3DS close button (`sheet.cancel`).

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
