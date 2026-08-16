package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.WalletParams
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class DigitalWalletsService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getParams(walletType: String, sessionToken: String): WalletParams {
        val path = SdkConstants.digitalWalletParamsPath(walletType)
        return WalletParams.fromApiMap(http.getJson(ApiUrl.make(env.apiNextBaseURL, path), sessionToken))
    }
}
