# xMoney Android example app

In-repo demo for the SDK. One Gradle module, a launcher, copy-paste samples, three merchant scenarios, and an internal playground.

Sun/moon toggle for **light / dark** (saved across Integrations, Advanced, and Playground). Scenario stores hide it and use their own accent. Samples pass a matching `AppearanceConfig` (`colorsLight` / `colorsDark`) so the SDK form sits on the merchant page instead of the library defaults.

## Run

```bash
cp example/secrets.properties.example example/secrets.properties
# Fill PUBLIC_KEY, API_KEY, API_BASE, CURRENCY, DESCRIPTION

./gradlew :example:assembleDebug
```

Open the **xmoney-android** root in Android Studio and run **example**.

Smoke-test published artifacts:

```bash
./gradlew :example:assembleDebug -PuseMavenSdk=true
```

## Launcher

| Section | Screen | What it is |
|---|---|---|
| Integrations | Payment Sheet | Drop-in sheet — copy this first |
| Integrations | Embedded Payment Element | Form in your layout |
| Integrations | Google Pay | Standalone wallet button |
| Example app | Lumen shop | Lifestyle catalog → cart → Payment Sheet |
| Example app | Hearth Café | Café menu → cart → Embedded Element |
| Example app | Pulse Studio | Memberships → cart → Embedded Element |
| Advanced | Payment Sheet (Activity API) | `PaymentSheet(config).present(activity, …)` |
| Advanced | Google Pay (Activity API) | `GooglePay(config).present()` / `updateOrder()` |
| Advanced | Merchant Pay button | Embedded form, your CTA via `confirm()` |
| Advanced | Update order | `updateOrder()` a new `PaymentIntent` on a mounted Element |
| Advanced | Card holder verification | Pre-pay name check |
| Advanced | Embedded in Views | XML + `ComposeView` |
| Internal | Playground | Every `PaymentConfig` / `AppearanceConfig` option. SDK Style is separate from the example sun/moon toggle (Auto follows that toggle). |

Integrations and name-check include a **Test cards** sheet — tap to copy Visa/Mastercard sandbox PANs, expiry, CVV, and 3DS (`00000`).

## Backend warning

[`DemoCheckoutBackend`](src/main/java/com/xmoney/example/backend/DemoCheckoutBackend.kt) sends `API_KEY` to the public demo server so this app can create orders without a merchant backend.

**Do not copy that into production.** The Android app should hold only `publicKey`. Your server creates the order and returns `payload` + `checksum`. The samples build `PaymentIntent` from those two values.

## Copy-paste notes

- Integrations samples **inline** `PaymentConfig` (`publicKey`, Google Pay, saved cards, optional `options.appearance`). Do not copy `defaultPaymentConfig()` — that helper is for the stores and playground.
- After `COMPLETE`, `FAILED`, or post-submit `CANCELED`, the order checksum is **consumed**. Create a new intent before paying again.
- Closing Payment Sheet **before** pay does not consume; present the same intent (**Continue**).
- Embedded / Google Pay: keep merchant loading until `EmbeddedEvent.Ready` / `GooglePayEvent.Ready`. Branch on `isOrderConsumed` after that. Pre-auth Google Pay dismiss delivers `canceled` and does not consume — present or tap again with the same intent.
- Payment Sheet / Google Pay Activity: keep the merchant Pay button loading until `Ready`. Samples use `PaymentSheetEvent.Processing` to tell pre-pay cancel apart from post-submit cancel.
- After a consumed result, samples hide the payment UI and show **New payment**.
- To change the amount on a mounted Element / Google Pay button, `updateOrder` with a new `PaymentIntent`. Do **not** set the intent to `null` or hide the form. Hearth and Pulse qty steppers pass a new intent into `PaymentElement`, which calls `updateOrder`. Pay stays locked (`isInteractionEnabled`) with its current title — `Processing` is an in-flight charge only. The form stays on screen.
- Activity Google Pay: while the host overlay is open, `GooglePay.updateOrder(nextIntent)` rebinds without dismissing. Throws if no host is open. A second `present()` is a no-op until `dismiss()`.
- Call `updateAppearance` / `updateStyle` / `updateWalletAppearance` (Element) or `updateAppearance` (Google Pay) when the example sun/moon theme changes. Recreating `PaymentConfig` does not restyle a live controller.
- [`exampleAppearance()`](src/main/java/com/xmoney/example/SampleHelpers.kt) is the appearance copy-paste — restyle Sheet / Element / Google Pay to match your chrome. `borderRadius` is the card field and payment-methods container radius.
- Cart amounts are **minor units** (`Long` cents). The demo backend converts to a decimal only at the HTTP boundary.
