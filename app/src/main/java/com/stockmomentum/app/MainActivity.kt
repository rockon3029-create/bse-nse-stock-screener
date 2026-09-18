package com.stockmomentum.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.tabs.TabLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class SortType {
    TOP_GAINERS,
    TOP_LOSERS,
    PRICE_LOW_HIGH,
    PRICE_HIGH_LOW,
    MONTHLY_GAINERS,
    NAME_A_Z
}

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: StockAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var tabLayout: TabLayout
    private lateinit var btnSort: Button
    private lateinit var tvCount: TextView

    private var allStocks = listOf<Stock>()
    private var selectedSector = "All"
    private var currentSort = SortType.TOP_GAINERS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        tabLayout = findViewById(R.id.tabLayout)
        btnSort = findViewById(R.id.btnSort)
        tvCount = findViewById(R.id.tvCount)

        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        adapter = StockAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchStocks() }

        btnSort.setOnClickListener { showSortDialog() }

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                selectedSector = tab?.text?.toString() ?: "All"
                filterAndRender()
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        fetchStocks()
    }

    private fun fetchStocks() {
        if (!swipeRefresh.isRefreshing) {
            progressBar.visibility = View.VISIBLE
        }
        scope.launch {
            allStocks = withContext(Dispatchers.IO) {
                StockRepository.fetchFilteredStocks()
            }
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            populateSectorTabs()
            filterAndRender()
        }
    }

    private fun populateSectorTabs() {
        tabLayout.removeAllTabs()
        tabLayout.addTab(tabLayout.newTab().setText("All"))

        val uniqueSectors = allStocks.map { it.sector }.distinct().sorted()
        for (sector in uniqueSectors) {
            tabLayout.addTab(tabLayout.newTab().setText(sector))
        }
    }

    private fun filterAndRender() {
        var list = if (selectedSector == "All") {
            allStocks
        } else {
            allStocks.filter { it.sector == selectedSector }
        }

        list = when (currentSort) {
            SortType.TOP_GAINERS -> list.sortedByDescending { it.changePercent }
            SortType.TOP_LOSERS -> list.sortedBy { it.changePercent }
            SortType.MONTHLY_GAINERS -> list.sortedByDescending { it.monthlyReturn }
            SortType.PRICE_LOW_HIGH -> list.sortedBy { it.price }
            SortType.PRICE_HIGH_LOW -> list.sortedByDescending { it.price }
            SortType.NAME_A_Z -> list.sortedBy { it.symbol }
        }

        tvCount.text = "${list.size} Shares Screened"
        adapter.submitList(list)
    }

    private fun showSortDialog() {
        val options = arrayOf(
            "Today's Top Gainers (% Change ⬇)",
            "Today's Top Losers (% Change ⬆)",
            "30-Day Momentum (Highest 30D Return)",
            "Price: Low to High (₹ ⬆)",
            "Price: High to Low (₹ ⬇)",
            "Alphabetical (A - Z)"
        )

        val selectedIndex = when (currentSort) {
            SortType.TOP_GAINERS -> 0
            SortType.TOP_LOSERS -> 1
            SortType.MONTHLY_GAINERS -> 2
            SortType.PRICE_LOW_HIGH -> 3
            SortType.PRICE_HIGH_LOW -> 4
            SortType.NAME_A_Z -> 5
        }

        AlertDialog.Builder(this)
            .setTitle("Sort Shares By")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                currentSort = when (which) {
                    0 -> SortType.TOP_GAINERS
                    1 -> SortType.TOP_LOSERS
                    2 -> SortType.MONTHLY_GAINERS
                    3 -> SortType.PRICE_LOW_HIGH
                    4 -> SortType.PRICE_HIGH_LOW
                    else -> SortType.NAME_A_Z
                }
                filterAndRender()
                dialog.dismiss()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

class StockAdapter : RecyclerView.Adapter<StockAdapter.StockViewHolder>() {
    private val items = mutableListOf<Stock>()

    fun submitList(newItems: List<Stock>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StockViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock, parent, false)
        return StockViewHolder(view)
    }

    override fun onBindViewHolder(holder: StockViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    class StockViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvSymbol: TextView = view.findViewById(R.id.tvSymbol)
        private val tvName: TextView = view.findViewById(R.id.tvName)
        private val tvPrice: TextView = view.findViewById(R.id.tvPrice)
        private val tvChange: TextView = view.findViewById(R.id.tvChange)
        private val tvSector: TextView = view.findViewById(R.id.tvSector)
        private val tv30DayReturn: TextView = view.findViewById(R.id.tv30DayReturn)
        private val tvExpectationBadge: TextView = view.findViewById(R.id.tvExpectationBadge)
        private val tvExpectationReason: TextView = view.findViewById(R.id.tvExpectationReason)
        private val tvNews: TextView = view.findViewById(R.id.tvNews)

        fun bind(stock: Stock) {
            tvSymbol.text = stock.symbol
            tvName.text = stock.name
            tvPrice.text = "₹${stock.price}"

            // Daily return
            val isUp = stock.changePercent >= 0
            tvChange.text = "${if (isUp) "+" else ""}${stock.changePercent}%"
            tvChange.setTextColor(if (isUp) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))

            tvSector.text = stock.sector

            // 30-Day return
            val isMonthUp = stock.monthlyReturn >= 0
            tv30DayReturn.text = "30D: ${if (isMonthUp) "+" else ""}${stock.monthlyReturn}%"
            tv30DayReturn.setTextColor(if (isMonthUp) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))

            // Expectation Badge & Reason
            tvExpectationBadge.text = stock.expectation.label
            tvExpectationBadge.setTextColor(Color.parseColor(stock.expectation.colorHex))
            tvExpectationReason.text = stock.expectationReason

            // News
            tvNews.text = stock.catalystNews ?: "Active volume momentum recorded"
        }
    }
}
