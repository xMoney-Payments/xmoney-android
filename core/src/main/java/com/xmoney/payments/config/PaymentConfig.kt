package com.xmoney.payments.config

import android.os.Parcelable
import com.xmoney.payments.model.CardHolderVerificationResult
import com.xmoney.payments.model.PaymentIntent
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

data class PaymentConfig(
    val publicKey: String,
    val card: CardConfig = CardConfig(),
    val paymentMethods: PaymentMethodsConfig = PaymentMethodsConfig(),
    val options: OptionsConfig = OptionsConfig(),
) {
    fun resolve(intent: PaymentIntent): ResolvedPaymentConfig = ResolvedPaymentConfig(
        publicKey = publicKey,
        orderPayload = intent.orderPayload,
        orderChecksum = intent.orderChecksum,
        card = card,
        paymentMethods = paymentMethods,
        options = options,
    )
}

@Parcelize
data class SavedCardsConfig(
    val enabled: Boolean = false,
    val optInVisible: Boolean = true,
) : Parcelable

enum class SubmitButtonType(val value: String) {
    BOOK("book"),
    BUY("buy"),
    CHECKOUT("checkout"),
    DONATE("donate"),
    ORDER("order"),
    PAY("pay"),
    SUBSCRIBE("subscribe"),
    TOP_UP("topUp"),
    DEPOSIT("deposit"),
    ;

    companion object {
        fun from(raw: String?): SubmitButtonType =
            entries.firstOrNull { it.value == raw } ?: PAY
    }
}

@Parcelize
data class SubmitButtonConfig(
    /** Embedded only. Payment Sheet always shows the SDK Pay button. */
    val visible: Boolean = true,
    val type: SubmitButtonType = SubmitButtonType.PAY,
) : Parcelable

enum class ValidationMode(val value: String) {
    ON_SUBMIT("onSubmit"),
    ON_CHANGE("onChange"),
    ON_BLUR("onBlur"),
    ON_TOUCHED("onTouched"),
    ;

    companion object {
        fun from(raw: String?): ValidationMode =
            entries.firstOrNull { it.value == raw } ?: ON_TOUCHED
    }
}

enum class CardGrouping(val value: String) {
    CONDENSED("condensed"),
    SPACED("spaced"),
    ;

    companion object {
        fun from(raw: String?): CardGrouping =
            entries.firstOrNull { it.value == raw } ?: CONDENSED
    }
}

enum class UserInterfaceStyle(val value: String) {
    AUTOMATIC("automatic"),
    ALWAYS_LIGHT("alwaysLight"),
    ALWAYS_DARK("alwaysDark"),
    ;

    companion object {
        fun from(raw: String?): UserInterfaceStyle =
            entries.firstOrNull { it.value == raw } ?: AUTOMATIC
    }
}

@Parcelize
data class CardInputsConfig(
    val grouping: CardGrouping = CardGrouping.CONDENSED,
) : Parcelable {
    val isSpaced: Boolean get() = grouping == CardGrouping.SPACED
}

@Parcelize
data class CardHolderName(
    val firstName: String,
    val middleName: String = "",
    val lastName: String,
) : Parcelable {
    fun toMap(): Map<String, Any?> = mapOf(
        "firstName" to firstName,
        "middleName" to middleName,
        "lastName" to lastName,
    )
}

@Parcelize
data class CardHolderVerification(
    val name: CardHolderName,
    @IgnoredOnParcel
    val onCardHolderVerification: (CardHolderVerificationResult) -> Boolean = { false },
) : Parcelable

@Parcelize
data class CardConfig(
    val savedCards: SavedCardsConfig = SavedCardsConfig(),
    val cardHolderVerification: CardHolderVerification? = null,
    val inputs: CardInputsConfig = CardInputsConfig(),
    val validationMode: ValidationMode = ValidationMode.ON_TOUCHED,
    val submitButton: SubmitButtonConfig = SubmitButtonConfig(),
) : Parcelable

@Parcelize
data class GooglePayConfig(
    val enabled: Boolean = false,
    val appearance: WalletAppearance = WalletAppearance(),
) : Parcelable

@Parcelize
data class PaymentMethodsConfig(
    val googlePay: GooglePayConfig = GooglePayConfig(),
) : Parcelable

@Parcelize
data class OptionsConfig(
    val locale: String = "en-US",
    val style: UserInterfaceStyle = UserInterfaceStyle.AUTOMATIC,
    val appearance: AppearanceConfig = AppearanceConfig(),
) : Parcelable

@Parcelize
data class ResolvedPaymentConfig(
    val publicKey: String,
    val orderPayload: String,
    val orderChecksum: String,
    val card: CardConfig = CardConfig(),
    val paymentMethods: PaymentMethodsConfig = PaymentMethodsConfig(),
    val options: OptionsConfig = OptionsConfig(),
) : Parcelable
