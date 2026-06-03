package com.example.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

object LanguageManager {
    val currentLanguage = mutableStateOf("ru")
}

@Composable
fun getLangText(ru: String, en: String): String {
    val lang = LanguageManager.currentLanguage.value
    return if (lang == "ru") ru else en
}
