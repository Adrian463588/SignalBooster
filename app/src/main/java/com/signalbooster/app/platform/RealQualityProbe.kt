package com.signalbooster.app.platform

import com.signalbooster.app.domain.interfaces.ProbeType
import com.signalbooster.app.domain.interfaces.QualityProbe
import com.signalbooster.app.domain.interfaces.SettingsRepository
import com.signalbooster.app.domain.models.MeasurementConfidence
import com.signalbooster.app.domain.models.ProbeScope
import com.signalbooster.app.domain.models.QualityMetrics
import com.signalbooster.app.domain.models.SignalQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Real platform implementation of QualityProbe using standard Java/Android networking APIs.
 * Uses dynamic user-configured endpoints and budgets from SettingsRepository.
 * Bounded by timeout and byte budget per AGENTS.md section 8 and PRD FR-02.
 */
@Singleton
class RealQualityProbe @Inject constructor(
    private val settingsRepository: SettingsRepository
) : QualityProbe {

    @Volatile
    private var isCancelled = false

    override suspend fun startProbe(
        probeType: ProbeType,
        timeoutMillis: Long,
        byteBudget: Long
    ): Flow<QualityMetrics> = flow {
        isCancelled = false

        // Read configured endpoint and budgets
        val configuredEndpoint = try {
            settingsRepository.probeEndpoint.first()
        } catch (_: Exception) {
            "https://connectivitycheck.gstatic.com/generate_204"
        }
        val targetHost = try {
            URL(configuredEndpoint).host
        } catch (_: Exception) {
            "connectivitycheck.gstatic.com"
        }

        val effectiveTimeout = timeoutMillis.coerceIn(1000L, 30000L).toInt()
        val effectiveByteBudget = byteBudget.coerceIn(102400L, 10485760L)

        emit(
            QualityMetrics(
                probeScope = when (probeType) {
                    ProbeType.DNS -> ProbeScope.DNS
                    ProbeType.TCP -> ProbeScope.TCP
                    ProbeType.HTTP -> ProbeScope.HTTP
                    ProbeType.THROUGHPUT -> ProbeScope.THROUGHPUT
                },
                measurementConfidence = MeasurementConfidence.LOW
            )
        )

        val samples = mutableListOf<Long>()
        var failedAttempts = 0
        val totalAttempts = when (probeType) {
            ProbeType.THROUGHPUT -> 1
            else -> 3
        }

        var throughputCalculatedMbps: Float? = null

        for (i in 0 until totalAttempts) {
            if (isCancelled) break

            try {
                when (probeType) {
                    ProbeType.DNS -> {
                        val duration = measureDnsLatency(targetHost)
                        samples.add(duration)
                    }
                    ProbeType.TCP -> {
                        val duration = measureTcpLatency(targetHost, 443, effectiveTimeout)
                        samples.add(duration)
                    }
                    ProbeType.HTTP -> {
                        val duration = measureHttpLatency(configuredEndpoint, effectiveTimeout)
                        samples.add(duration)
                    }
                    ProbeType.THROUGHPUT -> {
                        // Use public CDN 1MB stream or fallback to configured endpoint
                        val throughputTarget = if (configuredEndpoint.contains("gstatic")) {
                            "https://speed.cloudflare.com/__down?bytes=$effectiveByteBudget"
                        } else {
                            configuredEndpoint
                        }
                        val result = measureThroughput(
                            throughputTarget,
                            effectiveTimeout,
                            effectiveByteBudget
                        )
                        samples.add(result.latencyMs)
                        throughputCalculatedMbps = result.speedMbps
                    }
                }
            } catch (_: Exception) {
                failedAttempts++
            }
        }

        val lossRatio = if (totalAttempts > 0) failedAttempts.toFloat() / totalAttempts.toFloat() else 0f
        val avgLatency = if (samples.isNotEmpty()) samples.average().toInt() else null

        val jitter = if (samples.size >= 2) {
            var diffSum = 0L
            for (j in 0 until samples.size - 1) {
                diffSum += abs(samples[j + 1] - samples[j])
            }
            (diffSum / (samples.size - 1)).toInt()
        } else {
            0
        }

        val signalQuality = when {
            lossRatio > 0.5f || (avgLatency != null && avgLatency > 500) -> SignalQuality.POOR
            avgLatency != null && avgLatency > 200 -> SignalQuality.FAIR
            avgLatency != null && avgLatency > 80 -> SignalQuality.GOOD
            avgLatency != null -> SignalQuality.EXCELLENT
            else -> SignalQuality.NONE
        }

        val confidence = when {
            samples.size >= 3 && lossRatio == 0f -> MeasurementConfidence.HIGH
            samples.isNotEmpty() -> MeasurementConfidence.MEDIUM
            else -> MeasurementConfidence.LOW
        }

        val finalMetrics = QualityMetrics(
            latencyRttMs = avgLatency,
            jitterMs = jitter,
            lossRatio = lossRatio,
            throughputMbps = throughputCalculatedMbps,
            signalQuality = signalQuality,
            probeScope = when (probeType) {
                ProbeType.DNS -> ProbeScope.DNS
                ProbeType.TCP -> ProbeScope.TCP
                ProbeType.HTTP -> ProbeScope.HTTP
                ProbeType.THROUGHPUT -> ProbeScope.THROUGHPUT
            },
            measurementConfidence = confidence
        )

        emit(finalMetrics)
    }.flowOn(Dispatchers.IO)

    override suspend fun stopProbe() {
        isCancelled = true
    }

    private fun measureDnsLatency(host: String): Long {
        val start = System.currentTimeMillis()
        InetAddress.getAllByName(host)
        return (System.currentTimeMillis() - start).coerceAtLeast(1L)
    }

    private fun measureTcpLatency(host: String, port: Int, timeoutMs: Int): Long {
        val socket = Socket()
        val start = System.currentTimeMillis()
        try {
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            return (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun measureHttpLatency(endpointUrl: String, timeoutMs: Int): Long {
        val url = URL(endpointUrl)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "HEAD"
        conn.instanceFollowRedirects = false
        val start = System.currentTimeMillis()
        try {
            conn.connect()
            conn.responseCode
            return (System.currentTimeMillis() - start).coerceAtLeast(1L)
        } finally {
            conn.disconnect()
        }
    }

    private data class ThroughputResult(val latencyMs: Long, val speedMbps: Float)

    private fun measureThroughput(urlString: String, timeoutMs: Int, byteBudget: Long): ThroughputResult {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        val start = System.currentTimeMillis()
        var bytesReadTotal = 0L
        try {
            conn.connect()
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val inputStream: InputStream = conn.inputStream
            val buffer = ByteArray(8192)
            var bytes: Int
            val downloadStart = System.currentTimeMillis()
            while (inputStream.read(buffer).also { bytes = it } != -1) {
                bytesReadTotal += bytes
                if (bytesReadTotal >= byteBudget || (System.currentTimeMillis() - downloadStart) > timeoutMs) {
                    break
                }
            }
            val downloadDuration = (System.currentTimeMillis() - downloadStart).coerceAtLeast(1L)
            val speedMbps = (bytesReadTotal * 8f) / (downloadDuration / 1000f) / 1_000_000f
            return ThroughputResult(latency, speedMbps)
        } finally {
            conn.disconnect()
        }
    }
}
