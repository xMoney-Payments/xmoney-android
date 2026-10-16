# xMoney Payments — Android SDK

Native Android SDK for [xMoney](https://xmoney.com) checkout. Three surfaces, one `PaymentConfig`, one `PaymentResult`.

| Module              | Artifact                    | Use when                                                  |
| ------------------- | --------------------------- | --------------------------------------------------------- |
| **Payment Sheet**   | `com.xmoney:paymentsheet`   | Drop-in bottom sheet. SDK owns the UI and the Pay button. |
| **Payment Element** | `com.xmoney:paymentelement` | Card, saved cards, and Google Pay **in your layout**.     |
| **Google Pay**      | `com.xmoney:googlepay`      | Standalone wallet button or Activity `present()`.         |
| **Core**            | `com.xmoney:payments-core`  | Pulled in by the surfaces. Do not depend on it directly.  |

```
paymentsheet ──► paymentelement ──► payments-core
     │                                    ▲
     └──► googlepay ──────────────────────┘
```

`paymentelement` does not pull in Google Pay. Add `googlepay` next to it if the embedded form should offer a wallet button.

## Requirements

- Android Gradle Plugin 8.5+
- Kotlin 2.0+
- `minSdk` 23, `compileSdk` 36
- Java 17
- Host screens must be a `FragmentActivity`

## Installation

Latest release: **`1.1.0`** ([Maven Central](https://central.sonatype.com/search?q=g:com.xmoney))

```kotlin
dependencies {
    // Drop-in sheet (includes Element + Google Pay at runtime)
    implementation("com.xmoney:paymentsheet:1.1.0")

    // Or pick surfaces:
    // implementation("com.xmoney:paymentelement:1.1.0")
    // implementation("com.xmoney:googlepay:1.1.0")   // required for wallet in Embedded
}
```

`mavenCentral()` must be in your repositories.

Put only a **publishable** `publicKey` (`pk_test_…` / `pk_live_…`) in the app. Create orders on **your server**. Never ship a secret API key.

## Checkout flow

1. Your backend creates an order and returns `payload` + `checksum`.
2. The app builds a `PaymentIntent` from those two values.
3. You present Sheet, mount Element, or show Google Pay.
4. The SDK fetches the session token, collects payment, and runs 3DS if needed.
5. You handle `PaymentResult`. Session tokens are never passed by the merchant.

```kotlin
val intent = PaymentIntent(
    OrderPayload(payloadFromYourServer),
    OrderChecksum(checksumFromYourServer),
)
```

## Payment result

Every surface delivers the same sealed type:

```kotlin
when (result) {
    is PaymentResult.Complete -> { /* result.transaction */ }
    is PaymentResult.Failed -> { /* result.error.code / result.error.message */ }
    PaymentResult.Canceled -> { /* user dismissed; no error payload */ }
}
```

`Failed` messages are SDK-authored. Do not display raw server bodies.

Interim events (`Ready`, `Processing`) do not replace `onResult`. Use `Ready` to keep merchant loading visible until the checkout surface has drawn. `Processing` is an in-flight charge only — `updateOrder` does not emit it.

## Surface lifecycle contract

Shared with the iOS SDK:

1. **Ready.** Fires once per mounted surface, when the form or wallet button first draws, or after the 2-second fallback. A wallet that is known to be unavailable counts as a settled state, not an error, so Ready still fires. It never fires on a failure screen. If a later retry replaces the failure screen with a form, Ready fires then. Order updates never emit it.
2. **Initial failure.** The surface shows a themed, localized failure state. `Failed` is delivered once per failure streak, and direct callers get the error thrown. Re-renders must not retry. A merchant loader that waits for Ready must also end on `Failed`. A failed first load shows the SDK failure state and emits no Ready. To retry, call `updateOrder` with a new or the same order, or remount the surface.
3. **Later bind failure** (an intent is already bound, and binding a new intent fails). Pay stays locked on the stale order (`isInteractionEnabled == false`) until a successful bind. The surface emits `UpdateFailed(error)` and the error is thrown to direct callers. `onResult` is not called. A declarative host that switches back to the bound intent clears the lock; a remount does not.
4. **createOrder closure failure.** The previous order stays bound and payable. The error is thrown; no `UpdateFailed` event is emitted.
5. **Supersession.** A newer merchant call always wins, even if it passes the already-bound intent; it cancels any in-flight creation or bind. A rebind from the SDK's own surface (SwiftUI or Compose host) with the already-bound intent is a no-op and cancels nothing. A superseded call throws the platform cancellation error: `CancellationError` on iOS, `UpdateOrderSuperseded`, a subclass of `CancellationException`, on Android.
6. **During a charge.** The same intent is a no-op. A different intent throws "payment in progress", detected with a typed check rather than a string match. The declarative host keeps the intent and retries it once `Processing(false)` arrives.
7. **Processing events are never dropped** because a newer update is pending.
8. **`updateOrder(createOrder:)`** locks Pay immediately, runs the closure on the main thread, propagates the caller's cancellation, and returns the bound intent.
9. **Session bind is two-phase** (load, then commit). A superseded load never changes the session.
10. **Loader accessibility.** The coin has a localized "Loading" label and is announced politely. Covered form controls are hidden from accessibility.

On Android, "payment in progress" is `PaymentError.PaymentInProgress`, and `UpdateOrderSuperseded` is caught by `runCatching { }` and `catch (e: Exception)` — rethrow it, or check for it, before treating an update as failed.

## Surface behavior

| Surface | Ready | Initial failure | Later update failure | Same intent while paying |
| --- | --- | --- | --- | --- |
| Payment Element | After the form and, when offered, the Google Pay button have pre-drawn, or after two seconds | Localized failure text and `onResult(Failed)`, no `Ready` | `UpdateFailed`, throws; Pay locked on the stale order | No-op |
| Payment Sheet | After the first draw of that presentation, kept across activity recreation | `onResult(Failed)` and the sheet closes | No `updateOrder` | No `updateOrder` |
| Compose Google Pay | After the button pre-draws, or as soon as Google Pay is unavailable for a loaded order | Localized failure text and `onResult(Failed)`, no `Ready` | `UpdateFailed`, throws; button locked on the stale order | No-op |
| Activity Google Pay | After the first successful bind of that presentation | `onResult(Failed)` and the host finishes, no `Ready` | `UpdateFailed`, throws; wallet locked on the stale order | No-op |

## Order lifecycle

Order checksums are **one-shot**. After a consumed result the bound order cannot be charged again.

| Outcome                                                 | Consumes order? | What you do                                  |
| ------------------------------------------------------- | --------------- | -------------------------------------------- |
| `Complete`                                              | Yes             | New `PaymentIntent` for another payment      |
| `Failed`                                                | Yes             | New `PaymentIntent`                          |
| `Canceled` **after** pay / 3DS started                  | Yes             | New `PaymentIntent`                          |
| Sheet closed **before** pay (header, back, drag, scrim) | No              | Present the **same** intent again            |
| Google Pay wallet dismissed **before** authorization    | No              | Present / tap again with the **same** intent |

Embedded and standalone Google Pay stay mounted after a consumed result; Pay / wallet disable (`isOrderConsumed`). Unmount them, or `bind` / recompose with a **new** intent.

Payment Sheet dismisses on terminal results (including post-submit cancel). Present again with a new intent.

Use `PaymentSheetEvent.Processing` (or `isOrderConsumed` on Element / Google Pay) to tell pre-pay cancel apart from post-submit cancel.

## Payment Sheet

SDK owns the full checkout UI, including the Pay button (`SubmitButtonConfig.visible` is ignored).

**Compose**

```kotlin
val sheet = rememberPaymentSheet(
    configuration = PaymentConfig(
        publicKey = "pk_test…",
        paymentMethods = PaymentMethodsConfig(
            googlePay = GooglePayConfig(enabled = true),
        ),
        card = CardConfig(
            savedCards = SavedCardsConfig(enabled = true),
        ),
    ),
    onResult = { result -> /* PaymentResult */ },
)

sheet.present(intent) { event ->
    if (event is PaymentSheetEvent.Ready) /* hide merchant loading */
}
sheet.dismiss() // optional; idle close still cancels
```

**Activity**

```kotlin
val sheet = PaymentSheet(configuration)
sheet.present(activity, intent, onEvent = { /* Ready / Processing */ }, onResult = { /* */ })
```

The sheet keeps its loading coin up until the form and, when offered, the Google Pay button have pre-drawn, then emits `PaymentSheetEvent.Ready`.

While idle, the sheet can be dragged closed. Drag, back, and scrim lock while a charge is in flight. `dismiss()` waits for an in-flight charge; idle close still cancels. A second `present()` while idle replaces the open sheet (the previous present completes with `Canceled`); while a charge is in flight the second `present()` is a no-op. Do not treat that `Canceled` as “leave checkout” if you immediately presented again.

Copy-paste sample: [`PaymentSheetSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/PaymentSheetSampleActivity.kt) · Activity API: [`PaymentSheetActivitySample.kt`](example/src/main/java/com/xmoney/example/samples/PaymentSheetActivitySample.kt)

## Payment Element

Same form as the sheet, without the bottom-sheet chrome. Mount it in your layout. Embedded does not add outer content padding or a page fill — the host background shows through; supply your own page spacing. Keep merchant loading until `EmbeddedEvent.Ready` or `onResult(Failed)`; a failed first load shows the SDK failure text and emits no `Ready`. Do not bring the loader back when you retry: a repeat failure in the same streak does not call `onResult` again. Bind runs only while `PaymentElement` is composed, so leave it mounted at its real height and cover it — a zero-height slot cannot draw the Google Pay button. `Ready` fires after the card form and, when offered, the wallet button have pre-drawn. The element’s own coin stays up until `Ready`, swallows taps, and hides covered controls from accessibility. `Ready` fires once per mounted element; a later `updateOrder` does not emit it again or bring the coin back:

```kotlin
var ready by remember { mutableStateOf(false) }

val embedded = rememberEmbeddedPayment(
    configuration = configuration,
    onResult = { result ->
        // First-load failure: show the SDK failure text.
        if (result is PaymentResult.Failed) ready = true
    },
)

if (!ready) { /* merchant loader */ }
PaymentElement(
    controller = embedded,
    intent = intent,
    modifier = Modifier.padding(horizontal = 20.dp),
    onEvent = { event ->
        if (event is EmbeddedEvent.Ready) ready = true
    },
)
```

After `Complete` / `Failed` / post-submit `Canceled`, hide the element (or pass a new intent). Pre-pay cancel does not consume — keep it mounted.

**Recreated activity** — the element runs in your activity. If that activity is recreated while a payment is in flight (rotation, a locale or theme change), the next bind of the same order reports that payment's result through `onResult` instead of taking a second payment. The newly mounted surface emits its own `Ready`. This works within the same process. If Android kills the process (for example while the shopper is in their bank app), the payment result is lost on the device; your backend's transaction status or webhook is the source of truth.

Payment Sheet and the Google Pay host handle rotation themselves. If the process is killed during authentication, Payment Sheet reports the result when the shopper returns; it reaches you through the activity result (`PaymentSheetContract`), because the in-memory callback is gone with the old process.

### Update the order

[EmbeddedPaymentController.updateOrder] rebinds a new signed `PaymentIntent` on the mounted Element. Pay, `confirm()`, and Google Pay are no-ops until it returns (`isInteractionEnabled` is false). A newer `updateOrder` cancels the in-flight one. The Pay button keeps its current title; it does not show “Processing...”.

```kotlin
embedded.updateOrder(
    PaymentIntent(OrderPayload(payload), OrderChecksum(checksum)),
)
```

When the shopper changes the cart, pass the server call instead and use the intent it returns. Pay is locked from the call, including while your server creates the order, so a fast Pay tap cannot charge the previous order. If the lambda throws, the previous order stays bound and payable, and `onResult` is not called. If the new order fails to bind, Pay stays locked and `EmbeddedEvent.UpdateFailed` is emitted:

```kotlin
intent = embedded.updateOrder {
    val (payload, checksum) = backend.createOrder(cart)
    PaymentIntent(OrderPayload(payload), OrderChecksum(checksum))
}
```

Compose `PaymentElement` / `GooglePayButton` bind `intent` when it changes; passing the intent an `updateOrder` call already bound is a no-op. An intent passed during a charge is bound when the charge ends. Keep the surface mounted; do not set the intent to `null` or swap the form for a loader. Pay stays locked (`isInteractionEnabled`) until the bind finishes. The `modifier` applies to the container that holds both the coin and the form. `Ready` is emitted once per mounted surface, for the first draw, and not again on `updateOrder`. Gate a merchant-owned Pay button with `embedded.isInteractionEnabled`. Direct `EmbeddedPaymentController.updateOrder()` does not emit `Ready`.

Copy-paste sample: [`UpdateOrderSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/UpdateOrderSampleActivity.kt)

### Live appearance

`rememberEmbeddedPayment` keeps the controller across appearance changes. Call `updateAppearance` / `updateStyle` / `updateLocale` / `updateWalletAppearance` instead of recreating `PaymentConfig`:

```kotlin
LaunchedEffect(appearance) { embedded.updateAppearance(appearance) }
LaunchedEffect(style) { embedded.updateStyle(style) }
LaunchedEffect(wallet) { embedded.updateWalletAppearance(wallet) }
```

Payment Sheet snapshots config at `present()` — pass appearance on `PaymentConfig` and present again to replace an idle sheet.

### Merchant-owned Pay button

Embedded only. Hide the SDK button and call `confirm()` after `EmbeddedEvent.Ready`. End your loader on `onResult(Failed)` too:

```kotlin
card = CardConfig(
    submitButton = SubmitButtonConfig(visible = false),
)

val embedded = rememberEmbeddedPayment(
    configuration = configuration,
    onResult = { result ->
        // First-load failure: show the SDK failure text.
        if (result is PaymentResult.Failed) ready = true
    },
)

PaymentElement(
    controller = embedded,
    intent = intent,
    onEvent = { event ->
        if (event is EmbeddedEvent.Ready) ready = true
    },
)

Button(
    onClick = { embedded.confirm() },
    enabled = embedded.isInteractionEnabled,
) { Text("Pay") }
```

`confirm()` submits the currently selected method (new card or saved card). Google Pay still uses the wallet button. `isInteractionEnabled` is false during `updateOrder` and while a charge is in flight. `EmbeddedEvent.Processing` is the in-flight charge only.

Copy-paste sample: [`EmbeddedPaymentSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/EmbeddedPaymentSampleActivity.kt) · Merchant CTA: [`MerchantPayButtonSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/MerchantPayButtonSampleActivity.kt) · XML `ComposeView`: [`EmbeddedViewsSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/EmbeddedViewsSampleActivity.kt) · Update order: [`UpdateOrderSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/UpdateOrderSampleActivity.kt)

## Google Pay

**Compose**

Keep `GooglePayButton` composed at its real size under your loader. `GooglePayEvent.Ready` fires once per mounted button, after the button has pre-drawn, or as soon as Google Pay is known to be unavailable. A failed first load shows the SDK failure text and emits no `Ready`, so end your loader on `onResult(Failed)` too, and keep it down while you retry.

```kotlin
var ready by remember { mutableStateOf(false) }
val googlePay = rememberGooglePay(configuration, onResult = { result ->
    // First-load failure: show the SDK failure text.
    if (result is PaymentResult.Failed) ready = true
})

if (!ready) { /* merchant loader */ }
GooglePayButton(
    controller = googlePay,
    intent = intent,
    onEvent = { event ->
        if (event is GooglePayEvent.Ready) ready = true
    },
)

// After Ready, gate your own chrome with the same flags the button uses:
googlePay.isAvailable  // site / config allows Google Pay
googlePay.isReady      // Play Wallet has a usable method
googlePay.isInteractionEnabled  // false during updateOrder and while paying
googlePay.isOrderConsumed
```

**Activity**

```kotlin
val googlePay = GooglePay(configuration)

val flags = googlePay.availability(activity, intent)
if (flags.isAvailable && flags.isReady) {
    googlePay.present(
        activity,
        intent,
        onEvent = { event ->
            if (event is GooglePayEvent.Ready) /* hide merchant loading */
        },
        onResult = { /* */ },
    )
}

googlePay.updateOrder(nextIntent)
googlePay.updateOrder { backend.createOrder(cart) }
googlePay.dismiss()
```

`updateOrder` rebinds a new signed `PaymentIntent` on the open host without dismissing it. Its suspend return is completion; the closure overload returns the bound intent. The Activity host emits `Ready` once, after its first successful bind. The closure overload disables the wallet before server order creation. A newer update supersedes the in-flight one (`UpdateOrderSuperseded`). An `onEvent` passed here replaces the listener from `present()`. It throws if no host is open, or if a different order is requested while a charge is in flight; the already-bound intent is a no-op. A second `present()` while this instance’s host is open is a no-op — use `updateOrder()`, or `dismiss()` then `present()`.

Pre-auth dismiss delivers `PaymentResult.Canceled` and does **not** consume. Present or tap again with the same intent.

A new `PaymentIntent` on `GooglePayButton` calls `GooglePayController.updateOrder` (same as Element). When the cart changes, call `googlePay.updateOrder { backend.createOrder(cart) }` first so the wallet button is locked while your server creates the order, then pass the returned intent to `GooglePayButton`. Wallet chrome uses `GooglePayController.updateAppearance`.

Copy-paste sample: [`GooglePaySampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/GooglePaySampleActivity.kt) · Activity API: [`GooglePayActivitySample.kt`](example/src/main/java/com/xmoney/example/samples/GooglePayActivitySample.kt)

## Configuration

```kotlin
PaymentConfig(
    publicKey = "pk_test…",
    paymentMethods = PaymentMethodsConfig(
        googlePay = GooglePayConfig(
            enabled = true,
            appearance = WalletAppearance(
                color = WalletButtonColor.BLACK, // or WHITE
                type = WalletButtonType.PAY,
            ),
        ),
    ),
    card = CardConfig(
        savedCards = SavedCardsConfig(enabled = true, optInVisible = true),
        validationMode = ValidationMode.ON_TOUCHED,
        inputs = CardInputsConfig(grouping = CardGrouping.CONDENSED),
        submitButton = SubmitButtonConfig(
            visible = true,              // Embedded only
            type = SubmitButtonType.PAY, // book, buy, checkout, donate, …
        ),
    ),
    options = OptionsConfig(
        locale = "en-US",                // pay-button amount punctuation
        style = UserInterfaceStyle.AUTOMATIC,
        appearance = AppearanceConfig(
            colorsLight = AppearanceColors(/* hex strings */),
            colorsDark = AppearanceColors(/* hex strings */),
            borderRadius = 12f,          // card fields + methods container
            primaryButton = PrimaryButtonConfig(borderRadius = 12f),
        ),
    ),
)
```

**Card validation** (`card.validationMode`, default `ON_TOUCHED`):

| Mode         | When errors show                                                     |
| ------------ | -------------------------------------------------------------------- |
| `ON_TOUCHED` | None while first typing; on blur; then live. After Pay, always live. |
| `ON_CHANGE`  | Live from the first keystroke                                        |
| `ON_BLUR`    | On blur; frozen until the next blur (live after Pay)                 |
| `ON_SUBMIT`  | On Pay (then live)                                                   |

Pay uses current field validity, not an empty errors map. Cardholder name is always collected.

**Appearance** — pass `colorsLight` / `colorsDark` so the form matches your chrome. Card fields (condensed box and spaced inputs) use `appearance.borderRadius` (default 16 dp), `appearance.borderWidth`, and `colors.componentBorder`. Pay button radius comes from `appearance.primaryButton.borderRadius` (default a pill, `9999` dp). Pass `12` for a squircle. On a mounted Element, call `updateAppearance` / `updateStyle` / `updateWalletAppearance`. See [`exampleAppearance()`](example/src/main/java/com/xmoney/example/SampleHelpers.kt) for a copy-paste palette.

**Cookies** — the 3DS challenge needs WebView cookies. The SDK turns on third-party cookies for its own challenge WebView only and does not call `CookieManager.setAcceptCookie`. If your app turns cookies off app-wide, turn them back on before a payment, or the bank page may fail.

## Card holder verification

Optional pre-pay name check. Requires the site to have name-check validation enabled.

```kotlin
card = CardConfig(
    cardHolderVerification = CardHolderVerification(
        name = CardHolderName(firstName = "John", lastName = "Doe"),
        onCardHolderVerification = { result ->
            result.status == CardHolderMatchStatus.MATCHED
        },
    ),
)
```

Return `true` to continue pay, `false` to block. Sample: [`CardHolderVerificationSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/CardHolderVerificationSampleActivity.kt)

## Public API

Use only these merchant-facing types:

| Surface         | Types                                                                                                                                                                                                           |
| --------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Config / models | `PaymentConfig` and nested options, `PaymentIntent` / `OrderCredentials` / `OrderPayload` / `OrderChecksum`, `PaymentResult`, `PaymentError`, `Transaction`                                                     |
| Payment Sheet   | `PaymentSheet`, `rememberPaymentSheet`, `PaymentSheetEvent`                                                                                                                                                     |
| Payment Element | `PaymentElement`, `rememberEmbeddedPayment`, `EmbeddedPaymentController` (`updateOrder`, `confirm`, `updateAppearance`, `updateStyle`, `updateLocale`, `updateWalletAppearance`), `EmbeddedEvent`               |
| Google Pay      | `GooglePay` (`availability`, `present`, `updateOrder`, `dismiss`), `GooglePayAvailability`, `rememberGooglePay`, `GooglePayButton`, `GooglePayController` (`updateOrder`, `updateAppearance`), `GooglePayEvent` |

Everything else (`HttpClient`, services, 3DS host, `PaymentForm`, theme helpers) is library-internal.

## Example app

In-repo demo: copy-paste Integrations, Lumen / Hearth / Pulse stores, Activity / Views / name-check samples, and an internal playground. See [`example/README.md`](example/README.md) for the launcher map, secrets, and consumption notes.

```bash
cp example/secrets.properties.example example/secrets.properties
# PUBLIC_KEY, API_KEY, API_BASE, CURRENCY, DESCRIPTION

./gradlew :example:assembleDebug                          # local modules
./gradlew :example:assembleDebug -PuseMavenSdk=true       # published artifacts
```

Open the **`xmoney-android`** root in Android Studio and run **`example`**. Start with **Payment Sheet**, **Embedded Payment Element**, then **Google Pay**.

The example talks to a demo backend with `API_KEY` in the app. **Do not ship that pattern.** Production apps hold only `publicKey`; your server returns `payload` + `checksum`.

## Testing

```bash
./gradlew :core:test :googlepay:test :paymentelement:test :paymentsheet:test
```

Contract tests read `test-vectors/test-vectors.json`.

## Support

- Releases: [CHANGELOG.md](CHANGELOG.md)
- Security: [SECURITY.md](SECURITY.md) — report vulnerabilities to **it-team@xmoney.com**, not a public issue
- License: [MIT](LICENSE)
