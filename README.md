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

Latest release: **`0.0.1`** ([Maven Central](https://central.sonatype.com/search?q=g:com.xmoney))

```kotlin
dependencies {
    // Drop-in sheet (includes Element + Google Pay at runtime)
    implementation("com.xmoney:paymentsheet:0.0.1")

    // Or pick surfaces:
    // implementation("com.xmoney:paymentelement:0.0.1")
    // implementation("com.xmoney:googlepay:0.0.1")   // required for wallet in Embedded
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

Interim events (`Ready`, `Processing`) do not replace `onResult`. Use `Ready` to keep merchant loading visible until the surface is bound. `Processing` is an in-flight charge only — `updateOrder` does not emit it.

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
        publicKey = "test_pk_…",
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

While idle, the sheet can be dragged closed. Drag, back, and scrim lock while a charge is in flight. `dismiss()` waits for an in-flight charge; idle close still cancels.

Copy-paste sample: [`PaymentSheetSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/PaymentSheetSampleActivity.kt) · Activity API: [`PaymentSheetActivitySample.kt`](example/src/main/java/com/xmoney/example/samples/PaymentSheetActivitySample.kt)

## Payment Element

Same form as the sheet, without the bottom-sheet chrome. Mount it in your layout. Keep merchant loading until `EmbeddedEvent.Ready` — bind runs only while `PaymentElement` is composed, so keep it mounted (collapsed until ready):

```kotlin
var ready by remember { mutableStateOf(false) }

val embedded = rememberEmbeddedPayment(
    configuration = configuration,
    onResult = { /* PaymentResult */ },
)

if (!ready) { /* merchant loader */ }
PaymentElement(
    controller = embedded,
    intent = intent,
    onEvent = { event ->
        if (event is EmbeddedEvent.Ready) ready = true
    },
)
```

After `Complete` / `Failed` / post-submit `Canceled`, hide the element (or pass a new intent). Pre-pay cancel does not consume — keep it mounted.

### Update the order

[EmbeddedPaymentController.updateOrder] rebinds a new signed `PaymentIntent` on the mounted Element. Pay, `confirm()`, and Google Pay are no-ops until it returns (`isInteractionEnabled` is false). A newer `updateOrder` cancels the in-flight one. The Pay button keeps its current title; it does not show “Processing...”.

```kotlin
embedded.updateOrder(
    PaymentIntent(OrderPayload(payload), OrderChecksum(checksum)),
)
```

Compose `PaymentElement` / `GooglePayButton` call `updateOrder` when `intent` changes. Keep the surface mounted; do not set the intent to `null` or swap the form for a loader. Pay stays locked (`isInteractionEnabled`) until `Ready`. Gate a merchant-owned Pay button with `embedded.isInteractionEnabled`.

Copy-paste sample: [`UpdateOrderSampleActivity.kt`](example/src/main/java/com/xmoney/example/samples/UpdateOrderSampleActivity.kt)

### Live appearance

`rememberEmbeddedPayment` keeps the controller across appearance changes. Call `updateAppearance` / `updateStyle` / `updateLocale` / `updateWalletAppearance` instead of recreating `PaymentConfig`:

```kotlin
LaunchedEffect(appearance) { embedded.updateAppearance(appearance) }
LaunchedEffect(style) { embedded.updateStyle(style) }
LaunchedEffect(wallet) { embedded.updateWalletAppearance(wallet) }
```

Payment Sheet snapshots config at `present()` — pass appearance on `PaymentConfig` and present again if the sheet is not showing.

### Merchant-owned Pay button

Embedded only. Hide the SDK button and call `confirm()` after `EmbeddedEvent.Ready`:

```kotlin
card = CardConfig(
    submitButton = SubmitButtonConfig(visible = false),
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

```kotlin
val googlePay = rememberGooglePay(configuration, onResult = { /* */ })

var ready by remember { mutableStateOf(false) }
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
```

Pre-auth dismiss delivers `PaymentResult.Canceled` and does **not** consume. Present or tap again with the same intent.

A new `PaymentIntent` on `GooglePayButton` calls `GooglePayController.updateOrder` (same as Element). Wallet chrome uses `GooglePayController.updateAppearance`.

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

**Appearance** — pass `colorsLight` / `colorsDark` so the form matches your chrome. Pay button radius comes from `appearance.primaryButton.borderRadius` (default a pill, `9999` dp). Pass `12` for a squircle. On a mounted Element, call `updateAppearance` / `updateStyle` / `updateWalletAppearance`. See [`exampleAppearance()`](example/src/main/java/com/xmoney/example/SampleHelpers.kt) for a copy-paste palette.

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

| Surface         | Types                                                                                                                                                       |
| --------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Config / models | `PaymentConfig` and nested options, `PaymentIntent` / `OrderCredentials` / `OrderPayload` / `OrderChecksum`, `PaymentResult`, `PaymentError`, `Transaction` |
| Payment Sheet   | `PaymentSheet`, `rememberPaymentSheet`, `PaymentSheetEvent`                                                                                                 |
| Payment Element | `PaymentElement`, `rememberEmbeddedPayment`, `EmbeddedPaymentController` (`bind`, `confirm`, `updateAppearance`, `updateStyle`, `updateLocale`, `updateWalletAppearance`), `EmbeddedEvent` |
| Google Pay      | `GooglePay`, `GooglePayAvailability`, `rememberGooglePay`, `GooglePayButton`, `GooglePayController` (`bind`, `updateAppearance`), `GooglePayEvent` |

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
- Security: [SECURITY.md](SECURITY.md) — report vulnerabilities to **support@xmoney.com**, not a public issue
- License: [MIT](LICENSE)
