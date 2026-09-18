package com.stockmomentum.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UiState {
    object Loading : UiState
    data class Success(val stocks: List<Stock>) : UiState
    data class Error(val msg: String) : UiState
}

class StockViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _sector = MutableStateFlow<String?>(null)
    val sector: StateFlow<String?> = _sector.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val data = StockRepository.fetchFilteredStocks()
                _uiState.value = UiState.Success(data)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Network error")
            }
        }
    }

    fun setSector(selected: String?) {
        _sector.value = selected
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StockListScreen(viewModel: StockViewModel) {
    val state by viewModel.uiState.collectAsState()
    val activeSector by viewModel.sector.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("BSE/NSE (₹5 - ₹500)", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("Momentum & Catalyst Radar", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            when (val current = state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Fetching market quotes & catalysts...", fontSize = 13.sp)
                        }
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Error: ${current.msg}", color = MaterialTheme.colorScheme.error)
                    }
                }
                is UiState.Success -> {
                    val sectors = listOf("All") + current.stocks.map { it.sector }.distinct()
                    val filtered = if (activeSector == null || activeSector == "All") current.stocks else current.stocks.filter { it.sector == activeSector }

                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(sectors) { s ->
                            FilterChip(
                                selected = (s == "All" && activeSector == null) || (s == activeSector),
                                onClick = { viewModel.setSector(if (s == "All") null else s) },
                                label = { Text(s, fontSize = 12.sp) }
                            )
                        }
                    }

                    LazyColumn(
                        contentPadding = PaddingValues(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered) { stock ->
                            Card(
                                shape = RoundedCornerShape(10.dp),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(stock.symbol, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    stock.exchange,
                                                    fontSize = 10.sp,
                                                    modifier = Modifier
                                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                                )
                                            }
                                            Text(stock.name, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }

                                        Column(horizontalAlignment = Alignment.End) {
                                            Text("₹${stock.price}", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                                            val isUp = stock.changePercent >= 0
                                            Text(
                                                "${if (isUp) "+" else ""}${stock.changePercent}%",
                                                color = if (isUp) Color(0xFF00875A) else Color(0xFFDE350B),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(6.dp))
                                    SuggestionChip(
                                        onClick = {},
                                        label = { Text(stock.sector, fontSize = 10.sp) },
                                        modifier = Modifier.height(24.dp)
                                    )

                                    stock.catalystNews?.let { news ->
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text("MARKET ATTRACTION / CATALYST", fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(news, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
