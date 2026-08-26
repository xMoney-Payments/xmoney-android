package com.xmoney.payments.service

import com.xmoney.payments.config.SdkConstants
import com.xmoney.payments.config.PaymentEnvironment
import com.xmoney.payments.model.SiteConfig
import com.xmoney.payments.network.ApiUrl
import com.xmoney.payments.network.HttpClient
@androidx.annotation.RestrictTo(androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP)

class ConfigService(private val http: HttpClient, private val env: PaymentEnvironment) {
    suspend fun getSiteConfig(sessionToken: String): SiteConfig {
        val url = ApiUrl.make(env.apiNextBaseURL, SdkConstants.CONFIG_PATH)
        return SiteConfig.fromApiMap(http.getJson(url, sessionToken))
    }
}
