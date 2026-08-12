package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.SavedCard
import com.xmoney.payments.model.SavedCardsResponse
import com.xmoney.payments.network.HttpClient
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class CardsService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getCards(sessionToken: String): List<SavedCard> {
        val url = "${env.apiNextBaseURL}/${SdkConstants.CARDS_PATH}?hasToken=true"
        return SavedCardsResponse.fromApiMap(http.getJson(url, sessionToken)).data
    }

    suspend fun deleteCard(cardId: String, sessionToken: String) {
        val url = "${env.apiNextBaseURL}/${SdkConstants.CARDS_PATH}/$cardId"
        http.delete(url, sessionToken)
    }
}
