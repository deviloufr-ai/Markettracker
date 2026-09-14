package com.deviloufr.markettracker.data

/**
 * Curated shortlist of popular, liquid assets offered for one-tap selection in
 * the picker. Symbols follow Yahoo Finance conventions so they classify
 * correctly through `marketOf` (see the UI layer): `^` → indice, `=X` → devise,
 * `=F` → contrat à terme, `-USD`/… → crypto, otherwise action.
 *
 * The picker derives the market of each entry from its symbol and groups
 * accordingly; [Asset.group] only adds sub-sections inside the large Actions
 * category. Anything not listed here is reachable through the live Yahoo search
 * (see [PriceApi.searchSymbols]).
 */
object AssetCatalog {

    val all: List<Asset> = listOf(
        // --- Crypto (-USD) ---
        Asset("BTC-USD", "Bitcoin"),
        Asset("ETH-USD", "Ethereum"),
        Asset("SOL-USD", "Solana"),
        Asset("XRP-USD", "XRP"),
        Asset("ADA-USD", "Cardano"),
        Asset("DOGE-USD", "Dogecoin"),
        Asset("DOT-USD", "Polkadot"),
        Asset("LTC-USD", "Litecoin"),
        Asset("LINK-USD", "Chainlink"),
        Asset("AVAX-USD", "Avalanche"),
        Asset("BNB-USD", "BNB"),

        // --- Devises (=X) ---
        Asset("EURUSD=X", "Euro / Dollar US"),
        Asset("GBPUSD=X", "Livre / Dollar US"),
        Asset("USDJPY=X", "Dollar US / Yen"),
        Asset("USDCHF=X", "Dollar US / Franc suisse"),
        Asset("USDCAD=X", "Dollar US / Dollar canadien"),
        Asset("AUDUSD=X", "Dollar australien / Dollar US"),
        Asset("EURGBP=X", "Euro / Livre"),
        Asset("EURJPY=X", "Euro / Yen"),

        // --- Contrats à terme (=F) ---
        Asset("GC=F", "Or"),
        Asset("SI=F", "Argent"),
        Asset("HG=F", "Cuivre"),
        Asset("CL=F", "Pétrole brut (WTI)"),
        Asset("BZ=F", "Pétrole Brent"),
        Asset("NG=F", "Gaz naturel"),
        Asset("ES=F", "Future S&P 500"),
        Asset("NQ=F", "Future Nasdaq 100"),
        Asset("YM=F", "Future Dow Jones"),

        // --- Indices (^) ---
        Asset("^GSPC", "S&P 500"),
        Asset("^DJI", "Dow Jones"),
        Asset("^IXIC", "Nasdaq Composite"),
        Asset("^RUT", "Russell 2000"),
        Asset("^VIX", "VIX (volatilité)"),
        Asset("^FCHI", "CAC 40"),
        Asset("^GDAXI", "DAX 40"),
        Asset("^FTSE", "FTSE 100"),
        Asset("^STOXX50E", "Euro Stoxx 50"),
        Asset("^N225", "Nikkei 225"),
        Asset("^HSI", "Hang Seng"),

        // --- Actions : Tech US ---
        Asset("AAPL", "Apple", "Tech US"),
        Asset("MSFT", "Microsoft", "Tech US"),
        Asset("NVDA", "Nvidia", "Tech US"),
        Asset("GOOGL", "Alphabet (Google)", "Tech US"),
        Asset("AMZN", "Amazon", "Tech US"),
        Asset("META", "Meta", "Tech US"),
        Asset("TSLA", "Tesla", "Tech US"),
        Asset("NFLX", "Netflix", "Tech US"),
        Asset("AMD", "AMD", "Tech US"),

        // --- Actions : Finance US ---
        Asset("JPM", "JPMorgan Chase", "Finance US"),
        Asset("V", "Visa", "Finance US"),
        Asset("MA", "Mastercard", "Finance US"),
        Asset("GS", "Goldman Sachs", "Finance US"),

        // --- Actions : France (CAC 40) ---
        Asset("MC.PA", "LVMH", "France (CAC 40)"),
        Asset("OR.PA", "L'Oréal", "France (CAC 40)"),
        Asset("AIR.PA", "Airbus", "France (CAC 40)"),
        Asset("TTE.PA", "TotalEnergies", "France (CAC 40)"),
        Asset("SAN.PA", "Sanofi", "France (CAC 40)"),
        Asset("BNP.PA", "BNP Paribas", "France (CAC 40)"),

        // --- Actions : Europe ---
        Asset("SAP.DE", "SAP", "Europe"),
        Asset("ASML.AS", "ASML", "Europe"),
    )

    /**
     * Case-insensitive match on the symbol or name. Returns the whole catalog
     * for a blank query.
     */
    fun filter(query: String): List<Asset> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return all
        return all.filter { it.symbol.lowercase().contains(q) || it.name.lowercase().contains(q) }
    }
}
