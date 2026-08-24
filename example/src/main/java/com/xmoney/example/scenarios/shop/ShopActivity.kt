package com.xmoney.example.scenarios.shop

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.scenarios.merchant.LumenBrand
import com.xmoney.example.scenarios.merchant.MerchantStore
import com.xmoney.example.theme.ExampleTheme

class ShopActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                MerchantStore(
                    brand = LumenBrand,
                    onLeave = { finish() },
                )
            }
        }
    }
}
