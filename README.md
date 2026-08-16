# xMoney Payments — Android SDK

Native Android SDK for xMoney payments. Ships as Maven modules:

| Module | Artifact | Description |
|--------|----------|-------------|
| `core` | `com.xmoney:payments-core` | Networking, validation, payment engine, 3DS host |
| `googlepay` | `com.xmoney:googlepay` | Standalone Google Pay + shared wallet orchestration |
| `paymentelement` | `com.xmoney:paymentelement` | Merchant-hosted Payment Element (card, saved card, Google Pay) |
| `paymentsheet` | `com.xmoney:paymentsheet` | Drop-in payment sheet UI (hosts the embedded form) |

```
paymentsheet ──► paymentelement ──► googlepay ──► payments-core
example depends on paymentsheet + googlepay + paymentelement
```

## Package layout

- **payments** (`payments-core`): `config`, `model`, `network`, `service`, `validation`, `engine`, `threeds`, `util`
- **googlepay**: public root + `internal`, `ui`
- **paymentelement**: public root + `internal`, `ui`, `theme`
- **paymentsheet**: public root + `internal`, `ui`

## Public API

Integrate only these merchant-facing types:

| Surface | Types |
|---------|--------|
| Config / models | `PaymentConfig` (+ nested options), `PaymentIntent` / `OrderCredentials` / `OrderPayload` / `OrderChecksum`, `PaymentResult`, `PaymentError` |
| Payment Sheet | `PaymentSheet`, `rememberPaymentSheet`, `PaymentSheetEvent` |
| Payment Element | `PaymentElement`, `rememberEmbeddedPayment`, `EmbeddedPayment`, `EmbeddedEvent` |
| Google Pay | `GooglePay`, `rememberGooglePay`, `GooglePayButton`, `GooglePayController`, `GooglePayEvent` |

Everything else (`HttpClient`, services, `ThreeDSDialog`, `PaymentForm`, theme helpers, loaders) is library-internal — do not depend on it from app code.

## Requirements

- Android Gradle Plugin 8.5+
- Kotlin 2.0+
- `minSdk` 23, `compileSdk` 36
- Java 17

## Installation

Latest release: **`0.0.1`** ([Maven Central](https://central.sonatype.com/search?q=g:com.xmoney))

```kotlin
dependencies {
    // Drop-in sheet (pulls paymentelement → googlepay → payments-core)
    implementation("com.xmoney:paymentsheet:0.0.1")

    // Or pick surfaces individually:
    // implementation("com.xmoney:paymentelement:0.0.1")
    // implementation("com.xmoney:googlepay:0.0.1")
    // implementation("com.xmoney:payments-core:0.0.1")
}
```

`mavenCentral()` must be in your repositories (default for most Android projects).

## Quick start

### Payment Sheet

```kotlin
import com.xmoney.paymentsheet.rememberPaymentSheet
import com.xmoney.payments.config.PaymentConfig
import com.xmoney.payments.config.PaymentMethodsConfig
import com.xmoney.payments.config.GooglePayConfig
import com.xmoney.payments.model.PaymentIntent
import com.xmoney.payments.model.OrderCredentials
import com.xmoney.payments.model.OrderPayload
import com.xmoney.payments.model.OrderChecksum

val paymentSheet = rememberPaymentSheet(
    configuration = PaymentConfig(
        publicKey = "test_pk_…",
        paymentMethods = PaymentMethodsConfig(
            googlePay = GooglePayConfig(enabled = true),
        ),
    ),
    onResult = { result -> /* handle PaymentResult */ },
)

paymentSheet.present(
    PaymentIntent(
        OrderCredentials(
            orderPayload = OrderPayload(payload),
            orderChecksum = OrderChecksum(checksum),
        ),
    ),
)
```

### Standalone Google Pay

```kotlin
import com.xmoney.googlepay.rememberGooglePay
import com.xmoney.googlepay.GooglePayButton

val googlePay = rememberGooglePay(configuration, onResult = { /* */ })

GooglePayButton(controller = googlePay, intent = intent)
```

Or imperative:

```kotlin
GooglePay(configuration).present(activity, intent, onResult = { /* */ })
```

### Embedded Payment Element

```kotlin
import com.xmoney.paymentelement.rememberEmbeddedPayment
import com.xmoney.paymentelement.PaymentElement

val embedded = rememberEmbeddedPayment(configuration, onResult = { /* */ })

PaymentElement(controller = embedded, intent = intent)
```

### Card holder verification

Optional pre-payment name check (requires site `nameCheckValidationEnabled`). Cardholder name is always collected on the card form.

```kotlin
import com.xmoney.payments.config.CardConfig
import com.xmoney.payments.config.CardHolderName
import com.xmoney.payments.config.CardHolderVerification
import com.xmoney.payments.model.CardHolderMatchStatus

card = CardConfig(
    cardHolderVerification = CardHolderVerification(
        name = CardHolderName(firstName = "John", lastName = "Doe"),
        onCardHolderVerification = { result ->
            result.status == CardHolderMatchStatus.MATCHED
        },
    ),
)
```

### After payment (Embedded / standalone Google Pay)

Order checksums are **one-shot**. After `COMPLETE`, `FAILED`, or post-submit `CANCELED` (including 3DS abandon after pay):

- The SDK marks the session consumed (`isOrderConsumed`) and disables Pay / Google Pay.
- The element stays mounted until **you** unmount it or navigate away.
- Call `bind` / recompose with a **new** `PaymentIntent` to accept another payment.
- Closing Payment Sheet **before** pay (header cancel) does not hit the API; present again with the same intent if you still need it. After pay starts, abandon/cancel requires a new intent.

Payment Sheet dismisses on terminal results (including post-submit cancel); present again with a new intent for another checkout.

Session tokens are always fetched by the SDK — merchants never pass them.

## Example app

```bash
cp example/secrets.properties.example example/secrets.properties
# Edit PUBLIC_KEY, API_KEY, API_BASE, CURRENCY, DESCRIPTION

# Local modules (default — use while developing the SDK)
./gradlew :example:assembleDebug

# Published Maven artifacts (smoke-test what merchants get)
./gradlew :example:assembleDebug -PuseMavenSdk=true
```

Open the **`xmoney-android`** root in Android Studio and run **`example`**. Demos:

1. **Payment Sheet** — full checkout in a bottom sheet
2. **Google Pay only** — standalone button
3. **Embedded Payment Element** — full form in-app

## Testing

```bash
./gradlew :core:test :googlepay:test :paymentelement:test :paymentsheet:test
```

Contract tests read `test-vectors/test-vectors.json`.

## Support

See [CHANGELOG.md](CHANGELOG.md) for release history. Report security issues per [SECURITY.md](SECURITY.md).
