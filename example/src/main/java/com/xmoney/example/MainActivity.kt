package com.xmoney.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity

private sealed interface DemoRoute {
    data object Checkout : DemoRoute
    data object AppearancePlayground : DemoRoute
}

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val darkChrome = isSystemInDarkTheme()
            val appearanceState = remember { DemoAppearanceEditorState() }
            var route by remember { mutableStateOf<DemoRoute>(DemoRoute.Checkout) }

            DemoTheme(darkTheme = darkChrome) {
                when (route) {
                    DemoRoute.Checkout -> DemoCheckoutScreen(
                        appearanceState = appearanceState,
                        onCustomizeAppearance = {
                            route = DemoRoute.AppearancePlayground
                        },
                    )
                    DemoRoute.AppearancePlayground -> AppearancePlaygroundScreen(
                        initialState = appearanceState,
                        onBack = { route = DemoRoute.Checkout },
                        onApply = { draft ->
                            appearanceState.copyFrom(draft)
                            route = DemoRoute.Checkout
                        },
                    )
                }
            }
        }
    }
}
