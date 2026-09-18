package com.stockmomentum.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.xml.parsers.DocumentBuilderFactory

enum class Expectation(val label: String, val colorHex: String) {
    STRONG_RISE("EXPECTED TO RISE ▲", "#16A34A"),
    MILD_RISE("MILD RISE BIAS ↗", "#22C55E"),
    NEUTRAL("CONSOLIDATING ▬", "#64748B"),
    MILD_FALL("MILD PULLBACK ↘", "#EA580C"),
    STRONG_FALL("EXPECTED TO FALL ▼", "#DC2626")
}

data class Stock(
    val symbol: String,
    val name: String,
    val exchange: String,
    val price: Double,
    val changePercent: Double,
    val sector: String,
    val catalystNews: String?,
    val monthlyReturn: Double,
    val expectation: Expectation,
    val expectationReason: String
)

data class ConfiguredStock(
    val symbol: String,
    val sector: String,
    val name: String
)

object StockRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val GITHUB_CONFIG_URL =
        "https://raw.githubusercontent.com/rockon3029-create/bse-nse-stock-screener/main/stocks.json"

    suspend fun fetchFilteredStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val (minPrice, maxPrice, universe) = loadStockUniverse()

        val results = mutableListOf<Stock>()
        val chunks = universe.chunked(15)

        for (chunk in chunks) {
            val batchTasks = chunk.map { stock ->
                async {
                    fetchSingleStock(stock.symbol, stock.sector, stock.name, minPrice, maxPrice)
                }
            }
            results.addAll(batchTasks.awaitAll().filterNotNull())
        }

        results.sortedByDescending { it.changePercent }
    }

    private fun loadStockUniverse(): Triple<Double, Double, List<ConfiguredStock>> {
        var minPrice = 1.0
        var maxPrice = 500.0
        val stocks = mutableListOf<ConfiguredStock>()

        try {
            val request = Request.Builder()
                .url(GITHUB_CONFIG_URL)
                .header("Cache-Control", "no-cache")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val rawJson = response.body?.string() ?: ""
                val root = JSONObject(rawJson)
                minPrice = root.optDouble("minPrice", 1.0)
                maxPrice = root.optDouble("maxPrice", 500.0)

                val arr = root.getJSONArray("stocks")
                for (i in 0 until arr.length()) {
                    val item = arr.getJSONObject(i)
                    stocks.add(
                        ConfiguredStock(
                            symbol = item.getString("symbol"),
                            sector = item.getString("sector"),
                            name = item.getString("name")
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        if (stocks.isEmpty()) {
            stocks.add(ConfiguredStock("SUZLON", "Renewable Energy", "Suzlon Energy"))
            stocks.add(ConfiguredStock("YESBANK", "Banking", "Yes Bank"))
            stocks.add(ConfiguredStock("IRFC", "Financial Services", "Indian Railway Finance"))
            stocks.add(ConfiguredStock("ZOMATO", "Consumer Tech", "Zomato Ltd"))
        }

        return Triple(minPrice, maxPrice, stocks)
    }

    private fun fetchSingleStock(
        symbol: String,
        sector: String,
        name: String,
        minPrice: Double,
        maxPrice: Double
    ): Stock? {
        return try {
            // Request 1 month (30 days) of historical daily candles
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol.NS?interval=1d&range=1mo"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val chartResult = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0)
                val meta = chartResult.getJSONObject("meta")

                val price = meta.optDouble("regularMarketPrice", 0.0)
                val prevClose = meta.optDouble("chartPreviousClose", price)
                val change = if (prevClose > 0.0) ((price - prevClose) / prevClose) * 100.0 else 0.0

                if (price in minPrice..maxPrice) {
                    // Extract close prices over the past 30 days
                    val quoteObj = chartResult.getJSONObject("indicators").getJSONArray("quote").getJSONObject(0)
                    val closeArray = quoteObj.getJSONArray("close")
                    val closeHistory = mutableListOf<Double>()
                    for (i in 0 until closeArray.length()) {
                        if (!closeArray.isNull(i)) {
                            closeHistory.add(closeArray.getDouble(i))
                        }
                    }

                    val (monthlyReturn, expectation, reason) = analyze30DayTrend(price, closeHistory)
                    val newsHeadline = fetchNews(symbol)

                    Stock(
                        symbol = symbol,
                        name = name,
                        exchange = "NSE",
                        price = Math.round(price * 100.0) / 100.0,
                        changePercent = Math.round(change * 100.0) / 100.0,
                        sector = sector,
                        catalystNews = newsHeadline,
                        monthlyReturn = Math.round(monthlyReturn * 100.0) / 100.0,
                        expectation = expectation,
                        expectationReason = reason
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun analyze30DayTrend(currentPrice: Double, closeHistory: List<Double>): Triple<Double, Expectation, String> {
        if (closeHistory.size < 5) {
            return Triple(0.0, Expectation.NEUTRAL, "Insufficient 30-day historical data.")
        }

        val startPrice = closeHistory.first()
        val monthlyReturn = if (startPrice > 0) ((currentPrice - startPrice) / startPrice) * 100.0 else 0.0
        val avg30 = closeHistory.average()
        val recent10 = closeHistory.takeLast(10)
        val avgRecent = recent10.average()

        return when {
            // Strong upward breakout
            currentPrice > avgRecent && avgRecent > avg30 && monthlyReturn > 8.0 -> {
                Triple(
                    monthlyReturn,
                    Expectation.STRONG_RISE,
                    "Trading above 30D avg (+${monthlyReturn.toInt()}% 30D return) with bullish accumulation."
                )
            }
            // Mild positive momentum
            currentPrice >= avg30 && monthlyReturn > 1.0 -> {
                Triple(
                    monthlyReturn,
                    Expectation.MILD_RISE,
                    "Holding above 30-day moving base. Steady upward trend continuation expected."
                )
            }
            // Breakdown / Heavy selling
            currentPrice < avgRecent && avgRecent < avg30 && monthlyReturn < -7.0 -> {
                Triple(
                    monthlyReturn,
                    Expectation.STRONG_FALL,
                    "Trading below key 30-day averages (${monthlyReturn.toInt()}% monthly decline). Bearish pressure."
                )
            }
            // Mild weakness / Consolidation downside
            currentPrice < avg30 -> {
                Triple(
                    monthlyReturn,
                    Expectation.MILD_FALL,
                    "Facing resistance below 30-day average. Mild corrective pullback likely."
                )
            }
            // Range-bound
            else -> {
                Triple(
                    monthlyReturn,
                    Expectation.NEUTRAL,
                    "Sideways movement within recent 30-day support & resistance zones."
                )
            }
        }
    }

    private fun fetchNews(symbol: String): String {
        return try {
            val rssUrl = "https://news.google.com/rss/search?q=$symbol+share+stock+NSE&hl=en-IN&gl=IN&ceid=IN:en"
            val request = Request.Builder().url(rssUrl).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { res ->
                val xml = res.body?.string() ?: return "Heavy market interest"
                val factory = DocumentBuilderFactory.newInstance()
                val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
                val items = doc.getElementsByTagName("item")
                if (items.length > 0) {
                    val title = (items.item(0) as org.w3c.dom.Element).getElementsByTagName("title").item(0).textContent
                    title.substringBefore(" - ")
                } else {
                    "Strong momentum and buyer attraction"
                }
            }
        } catch (_: Exception) {
            "Strong trading volumes observed in recent sessions"
        }
    }
}
