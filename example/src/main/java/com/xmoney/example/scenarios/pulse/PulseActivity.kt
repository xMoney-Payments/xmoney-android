package com.xmoney.example.scenarios.pulse

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.scenarios.merchant.MerchantStore
import com.xmoney.example.scenarios.merchant.PulseBrand
import com.xmoney.example.theme.ExampleTheme

class PulseActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                MerchantStore(
                    brand = PulseBrand,
                    onLeave = { finish() },
                )
            }
        }
    }
}
