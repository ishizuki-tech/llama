package com.negi.nativelib

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/**
 * =============================================================================
 * LlamaCpuConfig
 * =============================================================================
 * JP: llama.cpp 用の推奨スレッド数を端末の big.LITTLE 構成から推定します。
 * EN: Estimate a recommended thread count for llama.cpp based on big.LITTLE layout.
 *
 * 方針 / Strategy:
 * 1) Prefer cpufreq policy clusters: /sys/devices/system/cpu/cpufreq/policy
 * 2) Fallback to per-CPU max freq:   /sys/devices/system/cpu/cpuN/cpufreq/cpuinfo_max_freq
 * 3) Fallback to /proc/cpuinfo "CPU part" grouping (coarse)
 * 4) Final fallback: heuristic from Runtime.availableProcessors()
 *
 * 結果は lazy で一度だけ計算し、以降はキャッシュを返します。
 * The value is computed once (lazy) and then cached.
 */
object LlamaCpuConfig {

    private const val LOG_TAG = "LlamaCpuConfig"

    /**
     * JP: 推奨スレッド数（最低 2、合計コア数を超えない）。
     * EN: Recommended threads (at least 2, never exceeding total cores).
     */
    val preferredThreadCount: Int by lazy {
        val total = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)
        val big = CpuProbe.detectHighPerfCoreCount()
            .coerceIn(1, total)
        // ヒューリスティック:
        // - 原則「高性能コア数」を上限
        // - 高発熱端末での暴れを避けるため total を超えない
        val recommended = big.coerceAtLeast(2)
        Log.d(LOG_TAG, "total=$total big=$big -> recommended=$recommended")
        recommended
    }
}

/**
 * 内部ユーティリティ: CPU 情報の収集
 * Internal utility for reading CPU/cluster info safely.
 */
private object CpuProbe {

    private const val LOG_TAG = "LlamaCpuConfig"

    // ---------- Public entry ----------

    /**
     * JP: 高性能コア（big cluster）のコア数を推定して返します。
     * EN: Estimate the number of high-performance cores (big cluster).
     */
    fun detectHighPerfCoreCount(): Int {
        // 1) policy clusters
        clustersFromPolicy()?.let { clusters ->
            val best = clusters.maxByOrNull { it.maxFreq }  // pick the fastest cluster
            if (best != null && best.cpuCount > 0) {
                Log.d(LOG_TAG, "policy clusters -> $clusters, pick=$best")
                return best.cpuCount
            }
        }

        // 2) per-CPU max freqs
        perCpuMaxFreqs()?.let { freqs ->
            if (freqs.isNotEmpty()) {
                val min = freqs.minOrNull()!!
                val bigCount = freqs.count { it > min }  // count cores faster than the slowest bin
                if (bigCount > 0) {
                    Log.d(LOG_TAG, "per-cpu freqs bins=${bins(freqs)} -> big=$bigCount")
                    return bigCount
                }
            }
        }

        // 3) /proc/cpuinfo "CPU part" grouping (very coarse)
        //    NOTE: "CPU variant" は性能差と相関が弱く、"CPU part" の方がまだマシ。
        cpuPartsFromProc()?.let { parts ->
            if (parts.isNotEmpty()) {
                // 最多数の "part" を big とみなす簡易ルールは誤判定しうるため、
                // ここでは「異なる part が複数ある場合、少数派を big とみなす」ヒューリスティックにします。
                // (例) LITTLE(4) + BIG(4) -> 4 を返す
                val grouped = parts.groupingBy { it }.eachCount()
                val maybeBig = grouped.minByOrNull { it.value }?.value ?: 0
                if (maybeBig > 0) {
                    Log.d(LOG_TAG, "cpu parts groups=$grouped -> big~=$maybeBig (heuristic)")
                    return maybeBig
                }
            }
        }

        // 4) final fallback
        val total = Runtime.getRuntime().availableProcessors()
        val heuristic = when {
            total >= 8 -> total / 2          // assume 4 big + 4 little
            total >= 6 -> 3
            total >= 4 -> 2
            else       -> 2
        }
        Log.d(LOG_TAG, "fallback heuristic from total=$total -> $heuristic")
        return heuristic
    }

    // ---------- (1) Read clusters by cpufreq policy ----------

    private data class Cluster(val policy: Int, val cpuCount: Int, val maxFreq: Int)

    /**
     * JP: policyX ディレクトリからクラスタ構成を読み取る（推奨・低コスト）。
     * EN: Read cluster layout from policyX directories (preferred, cheap).
     */
    private fun clustersFromPolicy(): List<Cluster>? = try {
        val base = File("/sys/devices/system/cpu/cpufreq")
        if (!base.isDirectory) null

        val clusters = base.listFiles { f -> f.isDirectory && f.name.startsWith("policy") }
            ?.mapNotNull { dir ->
                val policy = dir.name.removePrefix("policy").toIntOrNull() ?: return@mapNotNull null
                val related = readTextSafe(File(dir, "related_cpus")) ?: return@mapNotNull null
                val maxFreq = readTextSafe(File(dir, "cpuinfo_max_freq"))?.toIntOrNull() ?: return@mapNotNull null
                val count = parseCpuListCount(related)
                if (count > 0 && maxFreq > 0) Cluster(policy, count, maxFreq) else null
            }
            ?.takeIf { it.isNotEmpty() }

        clusters
    } catch (e: Exception) {
        Log.d(LOG_TAG, "clustersFromPolicy failed", e)
        null
    }

    // ---------- (2) Read per-CPU max frequencies ----------

    private fun perCpuMaxFreqs(): List<Int>? = try {
        val cpuDir = File("/sys/devices/system/cpu")
        val freqs = cpuDir.listFiles { f -> f.isDirectory && f.name.matches(Regex("^cpu\\d+$")) }
            ?.mapNotNull { cpu ->
                val maxPath = File(cpu, "cpufreq/cpuinfo_max_freq")
                readTextSafe(maxPath)?.toIntOrNull()
            }
            ?.filter { it > 0 }
            ?.sorted()

        freqs?.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.d(LOG_TAG, "perCpuMaxFreqs failed", e)
        null
    }

    // ---------- (3) Read /proc/cpuinfo CPU parts ----------

    private fun cpuPartsFromProc(): List<String>? = try {
        val lines = readLinesSafe("/proc/cpuinfo") ?: return null
        val parts = lines
            .asSequence()
            .filter { it.startsWith("CPU part") }
            .map { it.substringAfter(':').trim() } // e.g., 0xd0b
            .filter { it.isNotEmpty() }
            .toList()

        parts.takeIf { it.isNotEmpty() }
    } catch (e: Exception) {
        Log.d(LOG_TAG, "cpuPartsFromProc failed", e)
        null
    }

    // ---------- Helpers ----------

    private fun readTextSafe(file: File): String? = try {
        if (!file.exists()) null
        file.readText().trim()
    } catch (_: Throwable) { null }

    private fun readLinesSafe(path: String): List<String>? = try {
        BufferedReader(FileReader(path)).useLines { seq -> seq.toList() }
    } catch (_: Throwable) { null }

    /**
     * Parse CPU list strings like: "0-3", "0-1 2-3", "0 1 2 3"
     */
    private fun parseCpuListCount(s: String): Int {
        var count = 0
        val tokens = s.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        for (t in tokens) {
            val dash = t.indexOf('-')
            count += if (dash >= 0) {
                val a = t.substring(0, dash).toIntOrNull() ?: continue
                val b = t.substring(dash + 1).toIntOrNull() ?: continue
                (b - a + 1).coerceAtLeast(0)
            } else {
                1
            }
        }
        return count
    }

    private fun <T : Comparable<T>> bins(values: List<T>): Map<T, Int> =
        values.groupingBy { it }.eachCount()
}
