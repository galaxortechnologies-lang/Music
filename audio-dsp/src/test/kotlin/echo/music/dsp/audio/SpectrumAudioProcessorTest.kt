package echo.music.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import echo.music.dsp.core.SpectrumFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class SpectrumAudioProcessorTest {

  @Test
  fun passThroughPreservesPcmSamplesAndEmitsSpectrumFrame() {
    var latestFrame: SpectrumFrame? = null
    val processor =
      SpectrumAudioProcessor(emitIntervalMs = 0L) { frame ->
        latestFrame = frame
      }
    processor.analysisEnabled = true

    val format = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
    processor.configure(format)
    assertTrue(processor.isActive)

    val frameCount = 1024
    val input = ByteBuffer.allocateDirect(frameCount * 4).order(ByteOrder.nativeOrder())
    val expectedShorts = ShortArray(frameCount * 2)
    for (i in 0 until frameCount) {
      val sample = (18000.0 * sin(2.0 * PI * 440.0 * i / 44100.0)).toInt().toShort()
      expectedShorts[i * 2] = sample
      expectedShorts[i * 2 + 1] = sample
      input.putShort(sample)
      input.putShort(sample)
    }
    input.flip()

    processor.queueInput(input)
    val output = processor.getOutput().order(ByteOrder.nativeOrder())

    assertEquals(frameCount * 4, output.remaining())
    for (s in expectedShorts) {
      assertEquals(s, output.getShort())
    }
    assertTrue("Expected active spectrum frame from 440Hz sine PCM", latestFrame?.isActive == true)
  }
}
