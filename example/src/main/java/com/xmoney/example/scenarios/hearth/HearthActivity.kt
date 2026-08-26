package com.xmoney.example.scenarios.hearth

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.scenarios.merchant.HearthBrand
import com.xmoney.example.scenarios.merchant.MerchantStore
import com.xmoney.example.theme.ExampleTheme

class HearthActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                MerchantStore(
                    brand = HearthBrand,
                    onLeave = { finish() },
                )
            }
        }
    }
}
