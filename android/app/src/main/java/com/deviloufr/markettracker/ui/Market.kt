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
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.deviloufr.markettracker.ui.theme.CryptoTint
import com.deviloufr.markettracker.ui.theme.EquityTint
import com.deviloufr.markettracker.ui.theme.ForexTint
import com.deviloufr.markettracker.ui.theme.FuturesTint
import com.deviloufr.markettracker.ui.theme.IndexTint

/**
 * Classe d'actif d'un symbole, avec son icône, son libellé français et sa
 * couleur d'accent. La catégorie est déduite de la convention de nommage
 * Yahoo Finance (voir [marketOf]).
 */
enum class Market(val label: String, val icon: ImageVector, val tint: Color) {
    CRYPTO("Crypto", Icons.Filled.CurrencyBitcoin, CryptoTint),
    FOREX("Devises", Icons.Filled.CurrencyExchange, ForexTint),
    FUTURES("Contrats à terme", Icons.Filled.Schedule, FuturesTint),
    INDICE("Indice", Icons.Filled.Analytics, IndexTint),
    ACTION("Action", Icons.Filled.BusinessCenter, EquityTint);
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
 * Human-readable company/instrument name for a symbol: the curated catalog first
 * (instant, offline), then the live quote's name, then the symbol itself.
 */
fun displayName(symbol: String, quote: com.deviloufr.markettracker.data.Quote?): String =
    com.deviloufr.markettracker.data.AssetCatalog.nameOf(symbol)
        ?: quote?.name?.takeIf { it.isNotBlank() }
        ?: symbol.trim()

/**
 * Friendly exchange label for the card sub-line: the live quote's exchange
 * (normalised), else guessed from the Yahoo suffix, else the market category.
 */
fun exchangeLabel(symbol: String, quote: com.deviloufr.markettracker.data.Quote?): String {
    quote?.exchange?.takeIf { it.isNotBlank() }?.let { return prettyExchange(it) }
    return suffixExchange(symbol) ?: marketOf(symbol).label
}

/** Normalise Yahoo's exchange names/codes to something readable. */
private fun prettyExchange(raw: String): String = when (raw.uppercase()) {
    "NMS", "NGM", "NCM", "NASDAQGS", "NASDAQGM", "NASDAQCM", "NASDAQ" -> "Nasdaq"
    "NYQ", "NYE", "NYSE" -> "NYSE"
    "PCX", "ASE", "AMEX" -> "NYSE American"
    "PAR", "PARIS" -> "Euronext Paris"
    "AMS", "AMSTERDAM" -> "Euronext Amsterdam"
    "BRU", "BRUSSELS" -> "Euronext Bruxelles"
    "GER", "XETRA" -> "Xetra"
    "FRA", "FRANKFURT" -> "Francfort"
    "LSE", "LONDON" -> "London SE"
    "MIL", "MILAN" -> "Borsa Italiana"
    "MCE", "MADRID" -> "BME Madrid"
    "EBS", "SWX", "VTX" -> "SIX Swiss"
    "CCC", "CCY" -> "Marché mondial"
    else -> raw
}

/** Best-effort exchange from the Yahoo symbol suffix (used before a quote loads). */
private fun suffixExchange(symbol: String): String? {
    val s = symbol.trim().uppercase()
    return when {
        s.endsWith(".PA") -> "Euronext Paris"
        s.endsWith(".AS") -> "Euronext Amsterdam"
        s.endsWith(".BR") -> "Euronext Bruxelles"
        s.endsWith(".DE") -> "Xetra"
        s.endsWith(".F") -> "Francfort"
        s.endsWith(".L") -> "London SE"
        s.endsWith(".MI") -> "Borsa Italiana"
        s.endsWith(".MC") -> "BME Madrid"
        s.endsWith(".SW") -> "SIX Swiss"
        s.endsWith(".TO") -> "Toronto"
        else -> null
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
 * Asset badge: shows the symbol's real logo when one loads, and falls back to
 * the tinted Market category icon while loading, on error, or when no logo
 * source exists for that market. [shape] defaults to a circle; pass a rounded
 * square for the tile look used across the redesigned screens.
 */
@Composable
fun AssetLogo(
    symbol: String,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    shape: Shape = CircleShape
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
            .clip(shape)
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
                modifier = Modifier.size(size * 0.72f).clip(shape),
                loading = { fallback() },
                error = { fallback() }
            )
        }
    }
}
