package com.deviloufr.markettracker.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Classe d'actif d'un symbole, avec son icône, son libellé français et sa
 * couleur d'accent. La catégorie est déduite de la convention de nommage
 * Yahoo Finance (voir [marketOf]).
 */
enum class Market(val label: String, val icon: ImageVector, val tint: Color) {
    CRYPTO("Crypto", Icons.Filled.CurrencyBitcoin, Color(0xFFF7931A)),
    FOREX("Devises", Icons.Filled.CurrencyExchange, Color(0xFF16A34A)),
    FUTURES("Contrats à terme", Icons.Filled.Schedule, Color(0xFFB45309)),
    INDICE("Indice", Icons.Filled.Analytics, Color(0xFF7C3AED)),
    ACTION("Action", Icons.Filled.BusinessCenter, Color(0xFF0284C7));
}

/**
 * Déduit le marché d'un symbole à partir des suffixes/préfixes Yahoo Finance :
 * `^` → indice, `=X` → devises, `=F` → contrat à terme, `-USD`/`-EUR`/… → crypto,
 * sinon action.
 */
fun marketOf(symbol: String): Market {
    val s = symbol.trim().uppercase()
    return when {
        s.startsWith("^") -> Market.INDICE
        s.endsWith("=X") -> Market.FOREX
        s.endsWith("=F") -> Market.FUTURES
        Regex("-(USD|USDT|EUR|GBP|BTC|ETH)$").containsMatchIn(s) -> Market.CRYPTO
        else -> Market.ACTION
    }
}
