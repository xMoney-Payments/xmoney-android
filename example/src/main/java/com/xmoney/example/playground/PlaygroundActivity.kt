package com.xmoney.example.playground

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.xmoney.example.theme.ExampleTheme

private sealed interface PlaygroundRoute {
    data object Checkout : PlaygroundRoute
    data object Appearance : PlaygroundRoute
}

class PlaygroundActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExampleTheme {
                val appearanceState = remember { DemoAppearanceEditorState() }
                var route by remember { mutableStateOf<PlaygroundRoute>(PlaygroundRoute.Checkout) }
                when (route) {
                    PlaygroundRoute.Checkout -> DemoCheckoutScreen(
                        appearanceState = appearanceState,
                        onBack = { finish() },
                        onCustomizeAppearance = { route = PlaygroundRoute.Appearance },
                    )
                    PlaygroundRoute.Appearance -> AppearancePlaygroundScreen(
                        initialState = appearanceState,
                        onBack = { route = PlaygroundRoute.Checkout },
                        onApply = { draft ->
                            appearanceState.copyFrom(draft)
                            route = PlaygroundRoute.Checkout
                        },
                    )
                }
            }
        }
    }
}
