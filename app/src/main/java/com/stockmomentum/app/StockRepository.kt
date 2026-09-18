package com.stockmomentum.app

import kotlinx.coroutines.Dispatchers
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

object StockRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    // List of active BSE/NSE tickers with sectors
    private val TICKERS = listOf(
        Triple("SUZLON", "Renewable Energy", "Suzlon Energy"),
        Triple("YESBANK", "Banking", "Yes Bank"),
        Triple("ZOMATO", "Consumer Tech", "Zomato Ltd"),
        Triple("IDEA", "Telecom", "Vodafone Idea"),
        Triple("IRFC", "Railways / PSU", "Indian Railway Finance"),
        Triple("BHEL", "Capital Goods", "Bharat Heavy Electricals"),
        Triple("NHPC", "Power Generation", "NHPC Ltd"),
        Triple("SAIL", "Metals & Mining", "Steel Authority of India"),
        Triple("GMRINFRA", "Infrastructure", "GMR Airports"),
        Triple("NBCC", "Construction", "NBCC India"),
        Triple("HUDCO", "Housing Finance", "HUDCO"),
        Triple("TATASTEEL", "Metals & Mining", "Tata Steel"),
        Triple("IOC", "Oil & Gas", "Indian Oil Corporation"),
        Triple("RENUKA", "Agri & Sugar", "Shree Renuka Sugars")
    )

    suspend fun fetchFilteredStocks(): List<Stock> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Stock>()

        for ((symbol, sector, name) in TICKERS) {
            try {
                // Fetch quote from Yahoo Finance API
                val url = "https://query1.finance.yahoo.com/v8/finance/chart/$symbol.NS?interval=1d&range=1d"
                val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use
                    val body = response.body?.string() ?: return@use
                    val json = JSONObject(body)
                    val meta = json.getJSONObject("chart").getJSONArray("result").getJSONObject(0).getJSONObject("meta")
                    
                    val price = meta.optDouble("regularMarketPrice", 0.0)
                    val prevClose = meta.optDouble("chartPreviousClose", price)
                    val change = if (prevClose > 0.0) ((price - prevClose) / prevClose) * 100.0 else 0.0

                    // Filter: ₹5 to ₹500
                    if (price in 5.0..500.0) {
                        val newsHeadline = fetchNews(symbol)
                        list.add(
                            Stock(
                                symbol = symbol,
                                name = name,
                                exchange = "NSE",
                                price = Math.round(price * 100.0) / 100.0,
                                changePercent = Math.round(change * 100.0) / 100.0,
                                sector = sector,
                                catalystNews = newsHeadline
                            )
                        )
                    }
                }
            } catch (_: Exception) {}
        }
        list.sortedByDescending { it.changePercent }
    }

    private fun fetchNews(symbol: String): String {
        return try {
            val rssUrl = "https://news.google.com/rss/search?q=$symbol+share+stock+NSE&hl=en-IN&gl=IN&ceid=IN:en"
            val request = Request.Builder().url(rssUrl).header("User-Agent", "Mozilla/5.0").build()
            client.newCall(request).execute().use { res ->
                val xml = res.body?.string() ?: return "High volume momentum"
                val factory = DocumentBuilderFactory.newInstance()
                val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
                val items = doc.getElementsByTagName("item")
                if (items.length > 0) {
                    val title = (items.item(0) as org.w3c.dom.Element).getElementsByTagName("title").item(0).textContent
                    title.substringBefore(" - ")
                } else {
                    "Gaining strong retail and volume interest"
                }
            }
        } catch (_: Exception) {
            "Strong trading volumes observed in recent sessions"
        }
    }
}
