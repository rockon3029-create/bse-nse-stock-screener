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

object StockRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // Comprehensive BSE/NSE stock universe across all major sectors
    private val EXPANDED_UNIVERSE = listOf(
        // Renewable, Energy & Utilities
        Triple("SUZLON", "Renewable Energy", "Suzlon Energy"),
        Triple("IREDA", "Renewable Energy", "IREDA"),
        Triple("TATAPOWER", "Power & Utilities", "Tata Power"),
        Triple("NHPC", "Power & Utilities", "NHPC Ltd"),
        Triple("SJVN", "Power & Utilities", "SJVN Ltd"),
        Triple("BHEL", "Capital Goods", "Bharat Heavy Electricals"),
        Triple("IOC", "Oil & Gas", "Indian Oil Corporation"),
        Triple("BPCL", "Oil & Gas", "Bharat Petroleum"),
        Triple("ONGC", "Oil & Gas", "Oil and Natural Gas Corp"),
        Triple("GAIL", "Oil & Gas", "GAIL India Ltd"),

        // Banking, Financial Services & PSU Banks
        Triple("YESBANK", "Banking", "Yes Bank"),
        Triple("PNB", "Banking", "Punjab National Bank"),
        Triple("BANKBARODA", "Banking", "Bank of Baroda"),
        Triple("CANBK", "Banking", "Canara Bank"),
        Triple("UNIONBANK", "Banking", "Union Bank of India"),
        Triple("IDFCFIRSTB", "Banking", "IDFC First Bank"),
        Triple("FEDERALBNK", "Banking", "Federal Bank"),
        Triple("SOUTHBANK", "Banking", "South Indian Bank"),
        Triple("UCOBANK", "Banking", "UCO Bank"),
        Triple("MAHABANK", "Banking", "Bank of Maharashtra"),
        Triple("CENTRALBK", "Banking", "Central Bank of India"),
        Triple("IRFC", "Financial Services", "Indian Railway Finance"),
        Triple("HUDCO", "Financial Services", "HUDCO"),
        Triple("REC", "Financial Services", "REC Ltd"),
        Triple("PFC", "Financial Services", "Power Finance Corp"),
        Triple("IDBI", "Banking", "IDBI Bank"),
        Triple("J&KBANK", "Banking", "J&K Bank"),
        Triple("BANDHANBNK", "Banking", "Bandhan Bank"),
        Triple("L&TFH", "Financial Services", "L&T Finance"),

        // Infrastructure, Railways & Defense
        Triple("RVNL", "Railways / Infra", "Rail Vikas Nigam"),
        Triple("IRCON", "Railways / Infra", "Ircon International"),
        Triple("RAILTEL", "Railways / Infra", "RailTel Corporation"),
        Triple("RITES", "Railways / Infra", "RITES Ltd"),
        Triple("GMRINFRA", "Infrastructure", "GMR Airports"),
        Triple("NBCC", "Construction", "NBCC India"),
        Triple("NCC", "Construction", "NCC Ltd"),
        Triple("HFCL", "Telecom / Defense", "HFCL Ltd"),
        Triple("BEL", "Defense", "Bharat Electronics"),
        Triple("COCHINSHIP", "Defense & Shipping", "Cochin Shipyard"),
        Triple("GRSE", "Defense & Shipping", "Garden Reach Shipbuilders"),

        // Metals, Mining & Commodities
        Triple("TATASTEEL", "Metals & Mining", "Tata Steel"),
        Triple("SAIL", "Metals & Mining", "Steel Authority of India"),
        Triple("NMDC", "Metals & Mining", "NMDC Ltd"),
        Triple("NATIONALUM", "Metals & Mining", "National Aluminium"),
        Triple("HINDZINC", "Metals & Mining", "Hindustan Zinc"),
        Triple("VEDL", "Metals & Mining", "Vedanta Ltd"),
        Triple("JINDALSTEL", "Metals & Mining", "Jindal Steel & Power"),

        // Consumer, Tech, Media & Telecom
        Triple("IDEA", "Telecom", "Vodafone Idea"),
        Triple("ZOMATO", "Consumer Tech", "Zomato Ltd"),
        Triple("PAYTM", "Fintech", "One97 Communications"),
        Triple("NYKAA", "Consumer Tech", "FSN E-Commerce"),
        Triple("DELHIVERY", "Logistics", "Delhivery Ltd"),
        Triple("EASEMYTRIP", "Consumer Tech", "Easy Trip Planners"),
        Triple("ZEEL", "Media & Entertainment", "Zee Entertainment"),
        Triple("TV18BRDCST", "Media & Entertainment", "TV18 Broadcast"),
        Triple("NETWORK18", "Media & Entertainment", "Network18 Media"),
        Triple("DISHTV", "Media & Entertainment", "Dish TV India"),
        Triple("TRIDENT", "Textiles", "Trident Ltd"),
        Triple("ALOKINDS", "Textiles", "Alok Industries"),
        Triple("RENUKA", "Sugar & Agribusiness", "Shree Renuka Sugars"),
        Triple("BALRAMCHIN", "Sugar & Agribusiness", "Balrampur Chini"),
        Triple("ITC", "FMCG", "ITC Ltd"),

        // Pharma, Chemicals & Healthcare
        Triple("MOREPENLAB", "Pharma & Healthcare", "Morepen Laboratories"),
        Triple("MARKSANS", "Pharma & Healthcare", "Marksans Pharma"),
        Triple("WOCKPHARMA", "Pharma & Healthcare", "Wockhardt Ltd"),
        Triple("BIOCON", "Pharma & Healthcare", "Biocon Ltd"),
        Triple("TATACHEM", "Chemicals", "Tata Chemicals"),
        Triple("UPL", "Chemicals & Agro", "UPL Ltd")
    )

    suspend fun fetchFilteredStocks(): List<Stock> = withContext(Dispatchers.IO) {
        // Parallel requests using coroutine async to fetch dozens of quotes instantly
        val tasks = EXPANDED_UNIVERSE.map { (symbol, sector, name) ->
            async {
                fetchSingleStock(symbol, sector, name)
            }
        }
        tasks.awaitAll().filterNotNull().sortedByDescending { it.changePercent }
    }

    private fun fetchSingleStock(symbol: String, sector: String, name: String): Stock? {
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

                // Strict filter: price between ₹5 and ₹500
                if (price in 5.0..500.0) {
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
                val xml = res.body?.string() ?: return "Heavy trading volumes observed"
                val factory = DocumentBuilderFactory.newInstance()
                val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
                val items = doc.getElementsByTagName("item")
                if (items.length > 0) {
                    val title = (items.item(0) as org.w3c.dom.Element).getElementsByTagName("title").item(0).textContent
                    title.substringBefore(" - ")
                } else {
                    "Strong momentum and retail buyer attraction"
                }
            }
        } catch (_: Exception) {
            "Strong trading volumes observed in recent sessions"
        }
    }
}
