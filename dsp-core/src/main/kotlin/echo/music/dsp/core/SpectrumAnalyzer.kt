package echo.music.dsp.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Immutable snapshot of normalized audio spectrum magnitudes computed by [SpectrumAnalyzer].
 */
data class SpectrumFrame(
  val bands: List<Float>,
  val peakBands: List<Float>,
  val bassEnergy: Float,
  val midEnergy: Float,
  val highEnergy: Float,
  val sampleRateHz: Int,
  val isActive: Boolean,
) {
  /** Serializes this frame into compact JSON suitable for bridging into D3 visualization. */
  fun toJsonString(): String {
    val sb = StringBuilder(bands.size * 12 + 96)
    sb.append("{\"sampleRateHz\":").append(sampleRateHz)
    sb.append(",\"isActive\":").append(isActive)
    sb.append(",\"bassEnergy\":").append(formatFloat(bassEnergy))
    sb.append(",\"midEnergy\":").append(formatFloat(midEnergy))
    sb.append(",\"highEnergy\":").append(formatFloat(highEnergy))
    sb.append(",\"bands\":[")
    for (i in bands.indices) {
      if (i > 0) sb.append(',')
      sb.append(formatFloat(bands[i]))
    }
    sb.append("],\"peaks\":[")
    for (i in peakBands.indices) {
      if (i > 0) sb.append(',')
      sb.append(formatFloat(peakBands[i]))
    }
    sb.append("]}")
    return sb.toString()
  }

  private fun formatFloat(v: Float): String {
    val scaled = (v.coerceIn(0f, 1f) * 1000f).roundToInt()
    val whole = scaled / 1000
    val frac = scaled % 1000
    return "$whole.${frac.toString().padStart(3, '0')}"
  }

  companion object {
    const val DEFAULT_BAND_COUNT = 32

    val EMPTY =
      SpectrumFrame(
        bands = List(DEFAULT_BAND_COUNT) { 0f },
        peakBands = List(DEFAULT_BAND_COUNT) { 0f },
        bassEnergy = 0f,
        midEnergy = 0f,
        highEnergy = 0f,
        sampleRateHz = 44100,
        isActive = false,
      )
  }
}

/**
 * Pure-Kotlin real-time FFT spectrum analyzer for the `:dsp-core` pipeline.
 *
 * Uses a Hann window + iterative radix-2 Cooley-Tukey FFT mapped to logarithmically spaced
 * musical frequency bands (32 Hz – 16 kHz) with dBFS scaling and temporal envelope smoothing.
 */
class SpectrumAnalyzer(
  val bandCount: Int = SpectrumFrame.DEFAULT_BAND_COUNT,
  val fftSize: Int = 1024,
  private val minFreqHz: Double = 32.0,
  private val maxFreqHz: Double = 16000.0,
  private val minDb: Double = -62.0,
  private val maxDb: Double = -4.0,
) {
  init {
    require(bandCount > 0) { "bandCount must be positive" }
    require(fftSize >= 64 && (fftSize and (fftSize - 1)) == 0) {
      "fftSize must be a power of 2 >= 64"
    }
  }

  private val window =
    FloatArray(fftSize) { i -> (0.5 * (1.0 - cos(2.0 * PI * i / (fftSize - 1)))).toFloat() }
  private val re = FloatArray(fftSize)
  private val im = FloatArray(fftSize)
  private val magnitudes = FloatArray(fftSize / 2)
  private val smoothedBands = FloatArray(bandCount)
  private val peakBands = FloatArray(bandCount)

  /** Precomputed center frequencies (Hz) for display labels and testing. */
  val bandCenterFrequenciesHz: DoubleArray =
    DoubleArray(bandCount) { idx ->
      val ratio = (idx + 0.5) / bandCount.toDouble()
      minFreqHz * (maxFreqHz / minFreqHz).pow(ratio)
    }

  /**
   * Analyzes interleaved little-endian 16-bit PCM bytes, downmixing channels to mono on the fly.
   */
  @Synchronized
  fun analyzePcm16(
    pcmBytes: ByteArray,
    byteLength: Int = pcmBytes.size,
    sampleRateHz: Int,
    channelCount: Int,
  ): SpectrumFrame {
    val safeRate = if (sampleRateHz >= 8000) sampleRateHz else 44100
    val channels = channelCount.coerceAtLeast(1)
    val bytesPerFrame = channels * 2
    val usableBytes = min(byteLength, pcmBytes.size)
    val frameCount = usableBytes / bytesPerFrame
    if (frameCount <= 0) {
      return reset()
    }

    val samplesToRead = min(frameCount, fftSize)
    val startFrame = frameCount - samplesToRead
    val mono = FloatArray(fftSize)
    val destOffset = fftSize - samplesToRead

    for (f in 0 until samplesToRead) {
      val byteIndex = (startFrame + f) * bytesPerFrame
      var acc = 0f
      for (c in 0 until channels) {
        val pos = byteIndex + c * 2
        val lo = pcmBytes[pos].toInt() and 0xFF
        val hi = pcmBytes[pos + 1].toInt()
        val sampleShort = ((hi shl 8) or lo).toShort()
        acc += sampleShort.toFloat() / 32768f
      }
      mono[destOffset + f] = acc / channels
    }

    return analyzeMonoFloat(mono, safeRate)
  }

  /**
   * Analyzes a mono floating-point audio window in [-1.0, 1.0].
   */
  @Synchronized
  fun analyzeMonoFloat(samples: FloatArray, sampleRateHz: Int): SpectrumFrame {
    if (samples.isEmpty()) {
      return reset()
    }
    val safeRate = if (sampleRateHz >= 8000) sampleRateHz else 44100
    val usable = min(samples.size, fftSize)
    val srcStart = samples.size - usable

    var energySum = 0.0
    for (i in 0 until fftSize) {
      val sample = if (i < fftSize - usable) 0f else samples[srcStart + (i - (fftSize - usable))]
      energySum += sample * sample
      re[i] = sample * window[i]
      im[i] = 0f
    }

    val rms = sqrt(energySum / fftSize.toDouble())
    if (rms < 1e-6) {
      return decayFrame(safeRate)
    }

    fft(re, im)

    val binCount = fftSize / 2
    val scale = 2.0 / fftSize.toDouble()
    for (b in 0 until binCount) {
      val mag = sqrt((re[b] * re[b] + im[b] * im[b]).toDouble()) * scale
      magnitudes[b] = mag.toFloat()
    }

    val nyquist = safeRate / 2.0
    val effectiveMaxHz = min(maxFreqHz, nyquist * 0.96).coerceAtLeast(minFreqHz * 2.0)
    val dbSpan = (maxDb - minDb).coerceAtLeast(1.0)

    for (band in 0 until bandCount) {
      val lowEdge = minFreqHz * (effectiveMaxHz / minFreqHz).pow(band.toDouble() / bandCount)
      val highEdge = minFreqHz * (effectiveMaxHz / minFreqHz).pow((band + 1).toDouble() / bandCount)
      val startBin = max(1, (lowEdge * fftSize / safeRate).toInt())
      val endBin = min(binCount - 1, max(startBin, (highEdge * fftSize / safeRate).toInt()))

      var bandMag = 0.0
      for (bin in startBin..endBin) {
        val m = magnitudes[bin].toDouble()
        if (m > bandMag) bandMag = m
      }

      val db = if (bandMag > 1e-9) 20.0 * log10(bandMag) else minDb
      val normalized = ((db - minDb) / dbSpan).toFloat().coerceIn(0f, 1f)

      val prev = smoothedBands[band]
      val smoothed =
        if (normalized >= prev) {
          prev * 0.25f + normalized * 0.75f
        } else {
          prev * 0.72f + normalized * 0.28f
        }
      smoothedBands[band] = smoothed

      if (smoothed >= peakBands[band]) {
        peakBands[band] = smoothed
      } else {
        peakBands[band] = max(smoothed, peakBands[band] - 0.025f)
      }
    }

    val bassEnd = max(1, bandCount / 4)
    val midEnd = max(bassEnd + 1, (bandCount * 3) / 4)
    val bassEnergy = averageRange(smoothedBands, 0, bassEnd)
    val midEnergy = averageRange(smoothedBands, bassEnd, midEnd)
    val highEnergy = averageRange(smoothedBands, midEnd, bandCount)

    return SpectrumFrame(
      bands = smoothedBands.toList(),
      peakBands = peakBands.toList(),
      bassEnergy = bassEnergy,
      midEnergy = midEnergy,
      highEnergy = highEnergy,
      sampleRateHz = safeRate,
      isActive = smoothedBands.any { it > 0.01f },
    )
  }

  @Synchronized
  fun reset(): SpectrumFrame {
    smoothedBands.fill(0f)
    peakBands.fill(0f)
    return SpectrumFrame(
      bands = List(bandCount) { 0f },
      peakBands = List(bandCount) { 0f },
      bassEnergy = 0f,
      midEnergy = 0f,
      highEnergy = 0f,
      sampleRateHz = 44100,
      isActive = false,
    )
  }

  private fun decayFrame(sampleRateHz: Int): SpectrumFrame {
    var anyActive = false
    for (i in 0 until bandCount) {
      smoothedBands[i] = (smoothedBands[i] * 0.6f).let { if (it < 0.005f) 0f else it }
      peakBands[i] = max(smoothedBands[i], peakBands[i] - 0.04f).let { if (it < 0.005f) 0f else it }
      if (smoothedBands[i] > 0f) anyActive = true
    }
    val bassEnd = max(1, bandCount / 4)
    val midEnd = max(bassEnd + 1, (bandCount * 3) / 4)
    return SpectrumFrame(
      bands = smoothedBands.toList(),
      peakBands = peakBands.toList(),
      bassEnergy = averageRange(smoothedBands, 0, bassEnd),
      midEnergy = averageRange(smoothedBands, bassEnd, midEnd),
      highEnergy = averageRange(smoothedBands, midEnd, bandCount),
      sampleRateHz = sampleRateHz,
      isActive = anyActive,
    )
  }

  private fun averageRange(values: FloatArray, start: Int, endExclusive: Int): Float {
    val count = (endExclusive - start).coerceAtLeast(1)
    var sum = 0f
    for (i in start until min(endExclusive, values.size)) {
      sum += values[i]
    }
    return (sum / count).coerceIn(0f, 1f)
  }

  private fun fft(re: FloatArray, im: FloatArray) {
    val n = re.size
    var j = 0
    for (i in 1 until n) {
      var bit = n shr 1
      while (j and bit != 0) {
        j = j xor bit
        bit = bit shr 1
      }
      j = j or bit
      if (i < j) {
        val tr = re[i]
        re[i] = re[j]
        re[j] = tr
        val ti = im[i]
        im[i] = im[j]
        im[j] = ti
      }
    }

    var len = 2
    while (len <= n) {
      val angle = -2.0 * PI / len
      val wRe = cos(angle).toFloat()
      val wIm = sin(angle).toFloat()
      var i = 0
      while (i < n) {
        var curRe = 1f
        var curIm = 0f
        val half = len / 2
        for (k in 0 until half) {
          val uRe = re[i + k]
          val uIm = im[i + k]
          val idx = i + k + half
          val vRe = re[idx] * curRe - im[idx] * curIm
          val vIm = re[idx] * curIm + im[idx] * curRe
          re[i + k] = uRe + vRe
          im[i + k] = uIm + vIm
          re[idx] = uRe - vRe
          im[idx] = uIm - vIm
          val nextRe = curRe * wRe - curIm * wIm
          curIm = curRe * wIm + curIm * wRe
          curRe = nextRe
        }
        i += len
      }
      len = len shl 1
    }
  }
}
