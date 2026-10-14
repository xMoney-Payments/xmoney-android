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
- Embedded: keep merchant loading until `EmbeddedEvent.Ready` (the form has laid out and, when offered, the Google Pay button has drawn). Keep the surface laid out underneath; do not give it zero height. `Ready` fires once per mounted surface; remounting emits it again, while later order updates on the same surface do not. Branch on `isOrderConsumed` after that.
- Standalone Google Pay: keep merchant loading until `GooglePayEvent.Ready`. Pre-auth dismiss delivers `canceled` and does not consume — present or tap again with the same intent.
- Payment Sheet / Google Pay Activity: keep the merchant Pay button loading until `Ready`. Samples use `PaymentSheetEvent.Processing` to tell pre-pay cancel apart from post-submit cancel.
- After a consumed result, samples hide the payment UI and show **New payment**.
- To change the amount on a mounted Element or Google Pay button, pass the server call to `updateOrder { }`. Pay locks immediately, including during the debounce and server request, so a fast tap cannot charge the previous order. A newer call cooperatively cancels the older task, so keep the endpoint idempotent. Then pass the returned intent to the host; its repeated bind is a no-op. Do **not** set the intent to `null` or hide the form. Hearth, Pulse, Update order, and Playground use this flow. `Processing` is an in-flight charge only.
- Activity Google Pay: while the host overlay is open, `GooglePay.updateOrder(nextIntent)` rebinds without dismissing; use `updateOrder { createOrder() }` to lock before server work. Updates do not emit another `Ready`. Throws if no host is open or a different order is requested during payment. A second `present()` is a no-op until `dismiss()`.
- Call `updateAppearance` / `updateStyle` / `updateWalletAppearance` (Element) or `updateAppearance` (Google Pay) when the example sun/moon theme changes. Recreating `PaymentConfig` does not restyle a live controller.
- [`exampleAppearance()`](src/main/java/com/xmoney/example/SampleHelpers.kt) is the appearance copy-paste — restyle Sheet / Element / Google Pay to match your chrome. `borderRadius` is the card field and payment-methods container radius.
- Cart amounts are **minor units** (`Long` cents). The demo backend converts to a decimal only at the HTTP boundary.
