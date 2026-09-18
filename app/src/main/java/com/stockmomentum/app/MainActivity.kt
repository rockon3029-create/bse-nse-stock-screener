package com.stockmomentum.app

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var adapter: StockAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)

        adapter = StockAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh.setOnRefreshListener { fetchStocks() }
        fetchStocks()
    }

    private fun fetchStocks() {
        if (!swipeRefresh.isRefreshing) {
            progressBar.visibility = View.VISIBLE
        }
        scope.launch {
            val list = withContext(Dispatchers.IO) {
                StockRepository.fetchFilteredStocks()
            }
            progressBar.visibility = View.GONE
            swipeRefresh.isRefreshing = false
            adapter.submitList(list)
        }
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
        private val tvNews: TextView = view.findViewById(R.id.tvNews)

        fun bind(stock: Stock) {
            tvSymbol.text = stock.symbol
            tvName.text = stock.name
            tvPrice.text = "₹${stock.price}"
            val isUp = stock.changePercent >= 0
            tvChange.text = "${if (isUp) "+" else ""}${stock.changePercent}%"
            tvChange.setTextColor(if (isUp) Color.parseColor("#16A34A") else Color.parseColor("#DC2626"))
            tvSector.text = stock.sector
            tvNews.text = stock.catalystNews ?: "Heavy trading volume and attraction recorded."
        }
    }
}
