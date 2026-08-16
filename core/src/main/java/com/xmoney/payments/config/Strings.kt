package com.xmoney.payments.config

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Strings {
    fun text(key: String, locale: String, args: Map<String, String> = emptyMap()): String {
        val bundle = bundles[locale] ?: bundles["en-US"]!!
        var value = bundle[key] ?: bundles["en-US"]!![key] ?: key
        args.forEach { (placeholder, replacement) ->
            value = value.replace("{{$placeholder}}", replacement)
        }
        return value
    }

    fun submitButtonTitle(type: String, locale: String, amount: String?): String {
        val key = "button.$type"
        return if (!amount.isNullOrEmpty()) {
            text(key, locale, mapOf("amount" to amount))
        } else {
            text(key.replace(".pay", ".payNoAmount"), locale)
        }
    }

    fun formatAmount(amount: Double?, currency: String?): String? {
        if (amount == null) return null
        return try {
            val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
            format.currency = Currency.getInstance(currency ?: "EUR")
            format.format(amount)
        } catch (_: Exception) {
            amount.toString()
        }
    }

    private val en = mapOf(
        "sheet.title" to "Payment",
        "sheet.cancel" to "Cancel",
        "sheet.creditDebitCard" to "Credit / Debit Card",
        "sheet.poweredBy" to "Powered by",
        "sheet.savedCards" to "Saved cards",
        "sheet.payingWith" to "Paying with",
        "sheet.useAnotherCard" to "Use another card",
        "sheet.useOtherCard" to "Use other card",
        "sheet.or" to "or",
        "sheet.cardDetails" to "Card details",
        "sheet.enterCardDetails" to "Enter card details",
        "sheet.visaOrMastercard" to "Visa or Mastercard",
        "sheet.saveCardShort" to "Save card for next time",
        "sheet.delete" to "Delete",
        "sheet.edit" to "Edit",
        "sheet.done" to "Done",
        "sheet.removeCardConfirm" to "Are you sure you want to remove this card?",
        "sheet.remove" to "Remove",
        "sheet.keepIt" to "Keep it",
        "sheet.default" to "Default",
        "sheet.authentication" to "Authentication",
        "sheet.processingPayment" to "Processing your payment...",
        "elements.cardNumber" to "Card Number",
        "elements.cardholderName" to "Cardholder Name",
        "elements.expDate" to "Expiration Date",
        "elements.cvv" to "CVV",
        "elements.saveCard" to "Save Card for Future Use",
        "placeholder.cardNumber" to "0000 - 0000 - 0000 - 0000",
        "placeholder.expDate" to "MM / YY",
        "placeholder.cvv" to "000",
        "placeholder.cardholderName" to "Cardholder name",
        "errors.cardNumberRequired" to "Card number is required.",
        "errors.cardNumberUnsupported" to "Card type is not supported.",
        "errors.cardNumberTooShort" to "Card number is too short.",
        "errors.cardNumberWrongLength" to "Card number length is invalid.",
        "errors.cardNumberInvalid" to "Card number is invalid.",
        "errors.expDateRequired" to "Expiration date is required.",
        "errors.expDateInvalidFormat" to "Expiration date format is invalid.",
        "errors.expDateInvalidMonth" to "Expiration month is invalid.",
        "errors.cardExpired" to "Card is expired.",
        "errors.cvvRequired" to "CVV is required.",
        "errors.cvvInvalid" to "CVV is invalid.",
        "errors.cardHolderNameRequired" to "Cardholder name is required.",
        "errors.cardHolderNameNoDigits" to "The cardholder name cannot contain numbers.",
        "errors.cardHolderNameInvalidChars" to "Only letters, spaces, apostrophes, hyphens, and periods are allowed.",
        "button.pay" to "Pay {{amount}}",
        "button.payNoAmount" to "Pay",
        "button.processing" to "Processing...",
        "button.book" to "Book {{amount}}",
        "button.buy" to "Buy {{amount}}",
        "button.checkout" to "Checkout {{amount}}",
        "button.donate" to "Donate {{amount}}",
        "button.order" to "Place order {{amount}}",
        "button.subscribe" to "Subscribe for {{amount}}",
        "button.topUp" to "Top up {{amount}}",
        "button.deposit" to "Deposit {{amount}}",
    )

    private val el = mapOf(
        "sheet.title" to "Πληρωμή",
        "sheet.cancel" to "Ακύρωση",
        "sheet.creditDebitCard" to "Πιστωτική / Χρεωστική κάρτα",
        "sheet.poweredBy" to "Με την υποστήριξη",
        "sheet.savedCards" to "Αποθηκευμένες κάρτες",
        "sheet.payingWith" to "Πληρωμή με",
        "sheet.useAnotherCard" to "Χρήση άλλης κάρτας",
        "sheet.useOtherCard" to "Χρήση άλλης κάρτας",
        "sheet.or" to "ή",
        "sheet.cardDetails" to "Στοιχεία κάρτας",
        "sheet.enterCardDetails" to "Εισαγάγετε στοιχεία κάρτας",
        "sheet.visaOrMastercard" to "Visa ή Mastercard",
        "sheet.saveCardShort" to "Αποθήκευση κάρτας για επόμενη φορά",
        "sheet.delete" to "Διαγραφή",
        "sheet.edit" to "Επεξεργασία",
        "sheet.done" to "Τέλος",
        "sheet.removeCardConfirm" to "Είστε βέβαιοι ότι θέλετε να αφαιρέσετε αυτή την κάρτα;",
        "sheet.remove" to "Αφαίρεση",
        "sheet.keepIt" to "Διατήρηση",
        "sheet.default" to "Προεπιλογή",
        "sheet.authentication" to "Ταυτοποίηση",
        "sheet.processingPayment" to "Επεξεργασία πληρωμής...",
        "elements.cardNumber" to "Αριθμός κάρτας",
        "elements.cardholderName" to "Όνομα κατόχου",
        "elements.expDate" to "Ημερομηνία λήξης",
        "elements.cvv" to "CVV",
        "elements.saveCard" to "Αποθήκευση κάρτας για μελλοντική χρήση",
        "placeholder.cardNumber" to "0000 - 0000 - 0000 - 0000",
        "placeholder.expDate" to "ΜΜ / ΕΕ",
        "placeholder.cvv" to "000",
        "placeholder.cardholderName" to "Όνομα κατόχου",
        "errors.cardNumberRequired" to "Ο αριθμός κάρτας είναι υποχρεωτικός.",
        "errors.cardNumberUnsupported" to "Ο τύπος κάρτας δεν υποστηρίζεται.",
        "errors.cardNumberTooShort" to "Ο αριθμός κάρτας είναι πολύ σύντομος.",
        "errors.cardNumberWrongLength" to "Το μήκος του αριθμού κάρτας δεν είναι έγκυρο.",
        "errors.cardNumberInvalid" to "Ο αριθμός κάρτας δεν είναι έγκυρος.",
        "errors.expDateRequired" to "Η ημερομηνία λήξης είναι υποχρεωτική.",
        "errors.expDateInvalidFormat" to "Η μορφή της ημερομηνίας λήξης δεν είναι έγκυρη.",
        "errors.expDateInvalidMonth" to "Ο μήνας λήξης δεν είναι έγκυρος.",
        "errors.cardExpired" to "Η κάρτα έχει λήξει.",
        "errors.cvvRequired" to "Το CVV είναι υποχρεωτικό.",
        "errors.cvvInvalid" to "Το CVV δεν είναι έγκυρο.",
        "errors.cardHolderNameRequired" to "Το όνομα κατόχου είναι υποχρεωτικό.",
        "errors.cardHolderNameNoDigits" to "Το όνομα κατόχου δεν μπορεί να περιέχει αριθμούς.",
        "errors.cardHolderNameInvalidChars" to "Επιτρέπονται μόνο γράμματα, κενά, απόστροφοι, παύλες και τελείες.",
        "button.pay" to "Πληρωμή {{amount}}",
        "button.payNoAmount" to "Πληρωμή",
        "button.processing" to "Επεξεργασία...",
        "button.book" to "Κράτηση {{amount}}",
        "button.buy" to "Αγορά {{amount}}",
        "button.checkout" to "Ολοκλήρωση {{amount}}",
        "button.donate" to "Δωρεά {{amount}}",
        "button.order" to "Παραγγελία {{amount}}",
        "button.subscribe" to "Συνδρομή {{amount}}",
        "button.topUp" to "Φόρτιση {{amount}}",
        "button.deposit" to "Κατάθεση {{amount}}",
    )

    private val ro = mapOf(
        "sheet.title" to "Plată",
        "sheet.cancel" to "Anulează",
        "sheet.creditDebitCard" to "Card de credit / debit",
        "sheet.poweredBy" to "Oferit de",
        "sheet.savedCards" to "Carduri salvate",
        "sheet.payingWith" to "Plată cu",
        "sheet.useAnotherCard" to "Folosește alt card",
        "sheet.useOtherCard" to "Folosește alt card",
        "sheet.or" to "sau",
        "sheet.cardDetails" to "Detalii card",
        "sheet.enterCardDetails" to "Introduceți detaliile cardului",
        "sheet.visaOrMastercard" to "Visa sau Mastercard",
        "sheet.saveCardShort" to "Salvează cardul pentru data viitoare",
        "sheet.delete" to "Șterge",
        "sheet.edit" to "Editează",
        "sheet.done" to "Gata",
        "sheet.removeCardConfirm" to "Sigur doriți să eliminați acest card?",
        "sheet.remove" to "Elimină",
        "sheet.keepIt" to "Păstrează",
        "sheet.default" to "Implicit",
        "sheet.authentication" to "Autentificare",
        "sheet.processingPayment" to "Se procesează plata...",
        "elements.cardNumber" to "Număr card",
        "elements.cardholderName" to "Numele titularului",
        "elements.expDate" to "Data expirării",
        "elements.cvv" to "CVV",
        "elements.saveCard" to "Salvează cardul pentru utilizări viitoare",
        "placeholder.cardNumber" to "0000 - 0000 - 0000 - 0000",
        "placeholder.expDate" to "LL / AA",
        "placeholder.cvv" to "000",
        "placeholder.cardholderName" to "Numele titularului",
        "errors.cardNumberRequired" to "Numărul cardului este obligatoriu.",
        "errors.cardNumberUnsupported" to "Tipul de card nu este acceptat.",
        "errors.cardNumberTooShort" to "Numărul cardului este prea scurt.",
        "errors.cardNumberWrongLength" to "Lungimea numărului de card este invalidă.",
        "errors.cardNumberInvalid" to "Numărul cardului este invalid.",
        "errors.expDateRequired" to "Data expirării este obligatorie.",
        "errors.expDateInvalidFormat" to "Formatul datei de expirare este invalid.",
        "errors.expDateInvalidMonth" to "Luna de expirare este invalidă.",
        "errors.cardExpired" to "Cardul a expirat.",
        "errors.cvvRequired" to "CVV-ul este obligatoriu.",
        "errors.cvvInvalid" to "CVV-ul este invalid.",
        "errors.cardHolderNameRequired" to "Numele titularului este obligatoriu.",
        "errors.cardHolderNameNoDigits" to "Numele titularului nu poate conține cifre.",
        "errors.cardHolderNameInvalidChars" to "Sunt permise doar litere, spații, apostroafe, cratime și puncte.",
        "button.pay" to "Plătește {{amount}}",
        "button.payNoAmount" to "Plătește",
        "button.processing" to "Se procesează...",
        "button.book" to "Rezervă {{amount}}",
        "button.buy" to "Cumpără {{amount}}",
        "button.checkout" to "Finalizează {{amount}}",
        "button.donate" to "Donează {{amount}}",
        "button.order" to "Comandă {{amount}}",
        "button.subscribe" to "Abonare {{amount}}",
        "button.topUp" to "Alimentează {{amount}}",
        "button.deposit" to "Depune {{amount}}",
    )

    private val bundles = mapOf(
        "en-US" to en,
        "el-GR" to el,
        "ro-RO" to ro,
    )
}
