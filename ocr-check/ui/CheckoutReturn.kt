package com.makaohub.android.ui

import androidx.compose.runtime.staticCompositionLocalOf

val LocalCheckoutReturnSignal = staticCompositionLocalOf { 0 }
internal fun isPaystackReturnUri(value: String?) = value == "makaohub://payments/return"
