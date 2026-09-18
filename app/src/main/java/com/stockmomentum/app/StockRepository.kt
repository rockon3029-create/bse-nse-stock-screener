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

data class Stock(
    val symbol: String,
    val name: String,
    val exchange: String,
    val price: Double,
    val changePercent: Double,
    val sector: String,
    val catalystNews: String?
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

    // Corrected raw URL with proper username
    private const val GITHUB_CONFIG_URL =
        "https://raw.githubusercontent.com/rockon3029-create/bse-nse-stock-screener/main/stocks.json"

    suspend fun fetchFilteredStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val (minPrice, maxPrice, universe) = loadStockUniverse()

        // Fetch in batches of 15 to avoid mobile network timeouts / rate limits
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

        // Fallback default list if network is down on startup
        if (stocks.isEmpty()) {
            stocks.add(ConfiguredStock("SUZLON", "Renewable Energy", "Suzlon Energy"))
            stocks.add(ConfiguredStock("YESBANK", "Banking", "Yes Bank"))
            stocks.add(ConfiguredStock("IRFC", "Financial Services", "Indian Railway Finance"))
            stocks.add(ConfiguredStock("ZOMATO", "Consumer Tech", "Zomato Ltd"))
            stocks.add(ConfiguredStock("TATAPOWER", "Power & Utilities", "Tata Power"))
            stocks.add(ConfiguredStock("BHEL", "Capital Goods", "BHEL"))
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
            val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol.NS?interval=1d&range=1d"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")

                val price = meta.optDouble("regularMarketPrice", 0.0)
                val prevClose = meta.optDouble("chartPreviousClose", price)
                val change = if (prevClose > 0.0) ((price - prevClose) / prevClose) * 100.0 else 0.0

                if (price in minPrice..maxPrice) {
                    val newsHeadline = fetchNews(symbol)
                    Stock(
                        symbol = symbol,
                        name = name,
                        exchange = "NSE",
                        price = Math.round(price * 100.0) / 100.0,
                        changePercent = Math.round(change * 100.0) / 100.0,
                        sector = sector,
                        catalystNews = newsHeadline
                    )
                } else {
                    null
                }
            }
        } catch (_: Exception) {
            null
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
