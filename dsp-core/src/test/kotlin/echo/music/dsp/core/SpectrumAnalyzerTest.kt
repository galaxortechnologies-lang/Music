package echo.music.dsp.core

import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerTest {

  @Test
  fun silenceProducesZeroBandsAndInactiveFrame() {
    val analyzer = SpectrumAnalyzer(bandCount = 32, fftSize = 1024)
    val silence = FloatArray(1024) { 0f }

    val frame = analyzer.analyzeMonoFloat(silence, sampleRateHz = 44100)

    assertEquals(32, frame.bands.size)
    assertFalse(frame.isActive)
    assertEquals(0f, frame.bassEnergy, 0.0001f)
    assertEquals(0f, frame.midEnergy, 0.0001f)
    assertEquals(0f, frame.highEnergy, 0.0001f)
  }

  @Test
  fun lowFrequencySinePeaksInBassBands() {
    val analyzer = SpectrumAnalyzer(bandCount = 32, fftSize = 1024)
    val sampleRate = 44100
    val freqHz = 90.0
    val samples =
      FloatArray(1024) { i -> (0.85 * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat() }

    val frame = analyzer.analyzeMonoFloat(samples, sampleRateHz = sampleRate)

    assertTrue(frame.isActive)
    assertTrue("Expected bass energy > high energy", frame.bassEnergy > frame.highEnergy + 0.1f)
    val peakBandIndex = frame.bands.indices.maxByOrNull { frame.bands[it] } ?: -1
    assertTrue("Expected low-frequency peak band <= 10, got $peakBandIndex", peakBandIndex in 0..10)
  }

  @Test
  fun highFrequencySinePeaksInUpperBands() {
    val analyzer = SpectrumAnalyzer(bandCount = 32, fftSize = 1024)
    val sampleRate = 44100
    val freqHz = 6000.0
    val samples =
      FloatArray(1024) { i -> (0.85 * sin(2.0 * PI * freqHz * i / sampleRate)).toFloat() }

    val frame = analyzer.analyzeMonoFloat(samples, sampleRateHz = sampleRate)

    assertTrue(frame.isActive)
    assertTrue("Expected high energy > bass energy", frame.highEnergy > frame.bassEnergy + 0.1f)
    val peakBandIndex = frame.bands.indices.maxByOrNull { frame.bands[it] } ?: -1
    assertTrue("Expected high-frequency peak band >= 20, got $peakBandIndex", peakBandIndex >= 20)
  }

  @Test
  fun pcm16StereoBufferAnalysisProducesValidJson() {
    val analyzer = SpectrumAnalyzer(bandCount = 16, fftSize = 512)
    val sampleRate = 48000
    val frameCount = 512
    val pcm = ByteArray(frameCount * 4)
    for (i in 0 until frameCount) {
      val s = (24000.0 * sin(2.0 * PI * 440.0 * i / sampleRate)).toInt().toShort()
      val lo = (s.toInt() and 0xFF).toByte()
      val hi = ((s.toInt() shr 8) and 0xFF).toByte()
      val base = i * 4
      pcm[base] = lo
      pcm[base + 1] = hi
      pcm[base + 2] = lo
      pcm[base + 3] = hi
    }

    val frame = analyzer.analyzePcm16(pcm, pcm.size, sampleRateHz = sampleRate, channelCount = 2)
    val json = frame.toJsonString()

    assertTrue(frame.isActive)
    assertTrue(json.contains("\"sampleRateHz\":48000"))
    assertTrue(json.contains("\"bands\":["))
    assertTrue(json.contains("\"peaks\":["))
  }

  @Test
  fun resetClearsActiveStateAndBands() {
    val analyzer = SpectrumAnalyzer(bandCount = 16, fftSize = 512)
    val sine = FloatArray(512) { i -> (0.8 * sin(2.0 * PI * 300.0 * i / 44100.0)).toFloat() }
    assertTrue(analyzer.analyzeMonoFloat(sine, 44100).isActive)

    val cleared = analyzer.reset()
    assertFalse(cleared.isActive)
    assertTrue(cleared.bands.all { it == 0f })
  }
}
