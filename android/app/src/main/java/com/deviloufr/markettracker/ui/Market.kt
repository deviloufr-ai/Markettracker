package com.deviloufr.markettracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.BusinessCenter
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

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

/**
 * Best-effort URL for a symbol's real logo (e.g. NVDA → the NVIDIA logo):
 * Financial Modeling Prep for equities/ETFs (keyed by ticker) and CoinCap for
 * crypto (keyed by the base coin). Returns null for forex/futures/indices,
 * which have no per-asset logo — those fall back to the category icon.
 * Both endpoints are key-free and 404 on unknown symbols, so [AssetLogo]
 * degrades gracefully to the Material category icon.
 */
fun logoUrl(symbol: String): String? {
    val s = symbol.trim().uppercase()
    if (s.isEmpty()) return null
    return when (marketOf(s)) {
        Market.CRYPTO -> {
            val base = s.substringBefore('-').lowercase()
            base.takeIf { it.isNotEmpty() }?.let { "https://assets.coincap.io/assets/icons/$it@2x.png" }
        }
        Market.ACTION -> "https://financialmodelingprep.com/image-stock/$s.png"
        else -> null
    }
}

/**
 * Circular asset badge: shows the symbol's real logo when one loads, and falls
 * back to the tinted Market category icon while loading, on error, or when no
 * logo source exists for that market.
 */
@Composable
fun AssetLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp
) {
    val market = marketOf(symbol)
    val url = remember(symbol) { logoUrl(symbol) }

    val fallback: @Composable () -> Unit = {
        Icon(
            imageVector = market.icon,
            contentDescription = market.label,
            tint = market.tint,
            modifier = Modifier.size(size * 0.58f)
        )
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (url == null) {
            fallback()
        } else {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(url)
                    .crossfade(true)
                    .build(),
                contentDescription = "Logo $symbol",
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(size * 0.72f).clip(CircleShape),
                loading = { fallback() },
                error = { fallback() }
            )
        }
    }
}
