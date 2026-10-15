# Changelog

All notable changes to the xMoney Android SDK are documented here.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versioning follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- 3DS can hand the shopper to their bank app and keep waiting while the payment is confirmed.
- The shopper is asked before a bank app opens.
- `EmbeddedPaymentController.updateOrder { }`, `GooglePayController.updateOrder { }`, and Activity `GooglePay.updateOrder { }` take a lambda that creates the next order, and return the bound `PaymentIntent`. Pay is locked from the call, including while the lambda runs, until the new order is bound.
- `EmbeddedEvent.UpdateFailed` and `GooglePayEvent.UpdateFailed` report a failed bind after an order was already bound.
- `PaymentError.PaymentInProgress` (a `PaymentError.Payment`) is thrown when `updateOrder` asks for a different order during a charge.
- `UpdateOrderSuperseded`, a `CancellationException`, is thrown by an `updateOrder` call that a newer call replaced.
- The loading coin is announced as "Loading" by TalkBack, in the configured locale.
- `PaymentConfig.customerDetails` collects shopper details for card payments and Google Pay. Which fields appear comes from your account. Card and saved-card Pay stay blocked until the details page is valid. Google Pay requests the billing contact and sends it with the payment.

### Changed

- 3DS completion follows the transaction. The return page no longer closes the challenge.
- `browserLanguage` sent with a card payment is capped at 8 characters.
- Closing after a switch to the bank app waits for the bank's answer.
- The SDK no longer changes the app-wide WebView cookie setting. Only the 3DS WebView accepts third-party cookies.
- Browser headers are sent only with the card payment request.
- `Ready` on Payment Element, Payment Sheet, and the Compose Google Pay button is emitted after the card form has been laid out and, when Google Pay is offered, the wallet button has pre-drawn. It no longer fires when order bind returns.
- Payment Element and Payment Sheet keep a loading coin over the form until that first draw. The cover swallows taps without taking focus, and covered controls are hidden from accessibility and keyboard focus. The loading coin stays at its initial height. The surface resizes once, when the form and Google Pay button have drawn.
- Payment Sheet keeps `Ready` per presentation: recreating the sheet's activity does not show the coin or emit `Ready` again.
- Changing `options.locale` no longer recreates the Compose `GooglePayController`.
- SDK artifacts declare version constraints on each other, so upgrading one upgrades the others to the same version.

### Breaking changes

These follow the shared iOS/Android update-order contract:

1. **Ready.** Fires once per mounted surface, when the form or wallet button first draws, or after the 2-second fallback. A wallet that is known to be unavailable counts as a settled state, not an error, so Ready still fires. It never fires on a failure screen. If a later retry replaces the failure screen with a form, Ready fires then. Order updates never emit it.
2. **Initial failure.** The surface shows a themed, localized failure state. `Failed` is delivered once per failure streak, and direct callers get the error thrown. Re-renders must not retry.
3. **Later bind failure** (an intent is already bound, and binding a new intent fails). Pay stays locked on the stale order (`isInteractionEnabled == false`) until a successful bind. The surface emits `UpdateFailed(error)` and the error is thrown to direct callers. `onResult` is not called. A declarative host that switches back to the bound intent clears the lock; a remount does not.
4. **createOrder closure failure.** The previous order stays bound and payable. The error is thrown; no `UpdateFailed` event is emitted.
5. **Supersession.** A newer merchant call always wins, even if it passes the already-bound intent; it cancels any in-flight creation or bind. A rebind from the SDK's own surface (SwiftUI or Compose host) with the already-bound intent is a no-op and cancels nothing. A superseded call throws the platform cancellation error: `CancellationError` on iOS, `UpdateOrderSuperseded`, a subclass of `CancellationException`, on Android.
6. **During a charge.** The same intent is a no-op. A different intent throws "payment in progress", detected with a typed check rather than a string match. The declarative host keeps the intent and retries it once `Processing(false)` arrives.
7. **Processing events are never dropped** because a newer update is pending.
8. **`updateOrder(createOrder:)`** locks Pay immediately, runs the closure on the main thread, propagates the caller's cancellation, and returns the bound intent.
9. **Session bind is two-phase** (load, then commit). A superseded load never changes the session.
10. **Loader accessibility.** The coin has a localized "Loading" label and is announced politely. Covered form controls are hidden from accessibility.

Android-specific breaking changes:

- `EmbeddedEvent` and `GooglePayEvent` gained `UpdateFailed`. Exhaustive `when` expressions need a new branch.
- `EmbeddedPaymentController.updateOrder()` and `GooglePayController.updateOrder()` no longer emit `Ready`; `PaymentElement` and `GooglePayButton` do.
- An `onEvent` passed to Activity `GooglePay.updateOrder()` replaces the listener given to `present()` for the rest of that presentation, matching `EmbeddedPaymentController`.
- The `modifier` passed to `PaymentElement` now applies to an outer container that holds both the loading coin and the form, instead of the form itself. Size and scroll modifiers still apply to the whole element; min-size constraints no longer stretch the form.
- Controller `updateOrder` calls run on the main thread.
- `PaymentElement` and `GooglePayButton` no longer re-run `updateOrder` after every charge. An intent passed while a charge is running is bound once it ends.

### Migration

- End merchant loading on `onResult(Failed)` as well as `Ready`; a failed first load no longer emits `Ready`.
- Listen for `Ready` on `PaymentElement` or `GooglePayButton`, not on a direct controller `updateOrder()` call. Do not show a new loader that waits for `Ready` on later order updates; use `isInteractionEnabled` for update-order chrome.
- Treat the suspend return as update completion. Catch `UpdateOrderSuperseded` (or rethrow `CancellationException`) before treating an update as failed; `runCatching { }` and `catch (e: Exception)` catch it too. Keep order-creation endpoints idempotent.
- Use the intent returned by `updateOrder { }` as the new `intent` for `PaymentElement` / `GooglePayButton`.
- Handle `UpdateFailed`: show an error and call `updateOrder` again. Pay stays locked until a bind succeeds.
- Match `PaymentError.PaymentInProgress` instead of comparing the "Payment in progress" message.
- Activity Google Pay finishes its host when the first load fails. Wait for `onResult`, not for the activity to remain open with an error.
- Keep `PaymentElement` and `GooglePayButton` composed at their real size under merchant loading. A zero-height host cannot draw the Google Pay button and delays `Ready` until the two-second fallback.
- Upgrade all `com.xmoney` artifacts together. Version constraints now enforce this for Gradle builds.

### Fixed

- `PaymentElement` and the Compose `GooglePayButton` now unlock Pay when the host switches back to the bound intent after a failed update.
- The Google Pay button now follows a live change of wallet color, type or radius, such as a switch to dark mode, instead of keeping the look it was created with.
- An approval in the bank app is no longer reported as a failure after a dropped status check.
- The bank-app hand-off works on Android 11 and later.
- A load error on the return page no longer fails the payment.
- A challenge shown again after the activity is recreated no longer stays on screen with no payment behind it.
- A payment in progress is no longer lost when the screen rotates or the app is closed in the background during authentication.
- A screen closed during authentication no longer reports a canceled payment.
- The keyboard no longer covers the bank's code field.
- Bank links that open the bank app over https now open the app on Android 11 and later, except links to the merchant's own app and to card-network sites.
- Changing the language or font size during authentication no longer interrupts the challenge.
- Screenshots are blocked while the authentication page is shown.

### Example app

- In the example app, Pay could charge the previous order when tapped right after an amount change, before the new order was bound. The update-order samples now use `updateOrder { }`.
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
