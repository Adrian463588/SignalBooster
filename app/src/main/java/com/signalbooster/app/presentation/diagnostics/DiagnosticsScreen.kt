package com.signalbooster.app.presentation.diagnostics

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.signalbooster.app.domain.interfaces.ProbeType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Analytics,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Diagnostics & Metrics", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isTabletOrWide = maxWidth > 600.dp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (isTabletOrWide) 32.dp else 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Cellular Telemetry Card with Signal Strength Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CellTower,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "CELLULAR TELEMETRY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = uiState.cellularMetrics.technology ?: "Cellular",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // RSRP Signal Visual Bar
                        uiState.cellularMetrics.rsrp?.let { rsrp ->
                            SignalBar(
                                label = "LTE Signal Strength (RSRP)",
                                currentDbm = rsrp,
                                minDbm = -130,
                                maxDbm = -60
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        uiState.cellularMetrics.ssRsrp?.let { ssRsrp ->
                            SignalBar(
                                label = "5G SS-RSRP",
                                currentDbm = ssRsrp,
                                minDbm = -130,
                                maxDbm = -60
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        DetailRow("Operator", uiState.cellularMetrics.operator ?: "Unavailable")
                        DetailRow("LTE RSRP", uiState.cellularMetrics.rsrp?.let { "$it dBm" } ?: "Unavailable")
                        DetailRow("LTE RSRQ", uiState.cellularMetrics.rsrq?.let { "$it dB" } ?: "Unavailable")
                        DetailRow("LTE RSSNR", uiState.cellularMetrics.rssnr?.let { "$it dB" } ?: "Unavailable")
                        DetailRow("5G SS-RSRP", uiState.cellularMetrics.ssRsrp?.let { "$it dBm" } ?: "Unavailable")
                        DetailRow("5G SS-RSRQ", uiState.cellularMetrics.ssRsrq?.let { "$it dB" } ?: "Unavailable")
                        DetailRow("5G SS-SINR", uiState.cellularMetrics.ssSinr?.let { "$it dB" } ?: "Unavailable")
                        DetailRow("Physical Cell ID (PCI)", uiState.cellularMetrics.pci?.toString() ?: "Unavailable")
                    }
                }

                // 2. Wi-Fi Telemetry Card with RSSI Signal Bar
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Wifi,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "WI-FI TELEMETRY",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        uiState.wifiMetrics.rssi?.let { rssi ->
                            SignalBar(
                                label = "Wi-Fi RSSI",
                                currentDbm = rssi,
                                minDbm = -95,
                                maxDbm = -30
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }

                        DetailRow("SSID (Redacted)", uiState.wifiMetrics.ssid ?: "Unavailable")
                        DetailRow("RSSI", uiState.wifiMetrics.rssi?.let { "$it dBm" } ?: "Unavailable")
                        DetailRow("Frequency", uiState.wifiMetrics.frequency?.let { "$it MHz" } ?: "Unavailable")
                        DetailRow("Channel", uiState.wifiMetrics.channel?.toString() ?: "Unavailable")
                        DetailRow("Link Speed", uiState.wifiMetrics.linkSpeed?.let { "$it Mbps" } ?: "Unavailable")
                    }
                }

                // 3. Network Quality Probes Card with Responsive FlowRow
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Text(
                            text = "MANUAL NETWORK PROBES",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Runs isolated network probes to benchmark latency, jitter, loss, and speed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Responsive FlowRow for 4 Probe Buttons
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = if (isTabletOrWide) 4 else 2
                        ) {
                            ProbeButton("DNS", ProbeType.DNS, uiState.isProbing, modifier = Modifier.weight(1f)) {
                                viewModel.runProbe(ProbeType.DNS)
                            }
                            ProbeButton("TCP", ProbeType.TCP, uiState.isProbing, modifier = Modifier.weight(1f)) {
                                viewModel.runProbe(ProbeType.TCP)
                            }
                            ProbeButton("HTTP", ProbeType.HTTP, uiState.isProbing, modifier = Modifier.weight(1f)) {
                                viewModel.runProbe(ProbeType.HTTP)
                            }
                            ProbeButton("Speed", ProbeType.THROUGHPUT, uiState.isProbing, modifier = Modifier.weight(1f)) {
                                viewModel.runProbe(ProbeType.THROUGHPUT)
                            }
                        }

                        AnimatedVisibility(
                            visible = uiState.isProbing,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Executing ${uiState.lastProbeType?.name} probe...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        DetailRow("Measured Latency (RTT)", uiState.qualityMetrics.latencyRttMs?.let { "$it ms" } ?: "--")
                        DetailRow("Jitter", uiState.qualityMetrics.jitterMs?.let { "$it ms" } ?: "--")
                        DetailRow("Packet / Request Loss", uiState.qualityMetrics.lossRatio?.let { "${(it * 100).toInt()}%" } ?: "--")
                        DetailRow("Throughput Speed", uiState.qualityMetrics.throughputMbps?.let { String.format("%.2f Mbps", it) } ?: "--")
                        DetailRow("Signal Quality", uiState.qualityMetrics.signalQuality?.name ?: "--")

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedButton(
                            onClick = { viewModel.clearMetrics() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clear Probe History")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SignalBar(
    label: String,
    currentDbm: Int,
    minDbm: Int,
    maxDbm: Int
) {
    val progress = ((currentDbm - minDbm).toFloat() / (maxDbm - minDbm).toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "signal_bar"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "$currentDbm dBm", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        when {
                            progress >= 0.7f -> MaterialTheme.colorScheme.primary
                            progress >= 0.4f -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.error
                        }
                    )
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ProbeButton(
    label: String,
    probeType: ProbeType,
    isProbing: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    FilledTonalButton(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        enabled = !isProbing,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}