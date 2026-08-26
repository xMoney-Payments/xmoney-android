package com.xmoney.example.scenarios.merchant

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel

class MerchantStoreViewModel(
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private companion object {
        const val QTY_KEY = "quantities"
    }

    var quantities: Map<String, Int> by mutableStateOf(
        savedStateHandle.get<HashMap<String, Int>>(QTY_KEY)?.toMap().orEmpty(),
    )
        private set

    fun setQty(productId: String, qty: Int) {
        quantities = quantities.toMutableMap().apply {
            if (qty <= 0) remove(productId) else put(productId, qty)
        }
        savedStateHandle[QTY_KEY] = HashMap(quantities)
    }

    fun clear() {
        quantities = emptyMap()
        savedStateHandle[QTY_KEY] = HashMap<String, Int>()
    }
}
