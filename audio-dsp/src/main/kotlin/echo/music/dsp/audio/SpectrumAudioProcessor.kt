package echo.music.dsp.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import echo.music.dsp.core.SpectrumAnalyzer
import echo.music.dsp.core.SpectrumFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.min

/**
 * Pass-through Media3 [AudioProcessor] that taps real-time decoded 16-bit PCM audio frames
 * without modifying audio samples and computes normalized frequency spectrum bands via
 * `:dsp-core`'s [SpectrumAnalyzer].
 */
@UnstableApi
class SpectrumAudioProcessor(
  private val analyzer: SpectrumAnalyzer = SpectrumAnalyzer(),
  private val emitIntervalMs: Long = 33L,
  private val onSpectrumFrame: (SpectrumFrame) -> Unit = {},
) : AudioProcessor {

  @Volatile var analysisEnabled: Boolean = false
    set(value) {
      field = value
      if (!value) {
        onSpectrumFrame(analyzer.reset())
      }
    }

  private var sampleRate = 44100
  private var channelCount = 2
  private var encoding = C.ENCODING_INVALID
  private var isActive = false
  private var inputEnded = false

  private var outputBuffer: ByteBuffer = EMPTY_BUFFER
  private var scratchBytes = ByteArray(8192)
  private var lastEmitTimeMs = 0L

  override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
    sampleRate = inputAudioFormat.sampleRate
    channelCount = inputAudioFormat.channelCount
    encoding = inputAudioFormat.encoding
    if (encoding != C.ENCODING_PCM_16BIT || channelCount < 1) {
      throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
    }
    isActive = true
    return inputAudioFormat
  }

  override fun isActive(): Boolean = isActive

  override fun queueInput(inputBuffer: ByteBuffer) {
    val remaining = inputBuffer.remaining()
    if (remaining == 0) return

    if (outputBuffer === EMPTY_BUFFER || outputBuffer.capacity() < remaining) {
      outputBuffer = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
    } else {
      outputBuffer.clear()
    }

    if (analysisEnabled && encoding == C.ENCODING_PCM_16BIT) {
      val now = System.currentTimeMillis()
      if (now - lastEmitTimeMs >= emitIntervalMs) {
        lastEmitTimeMs = now
        val dup = inputBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val maxWindowBytes = analyzer.fftSize * channelCount * 2
        val bytesToInspect = min(remaining, maxWindowBytes)
        if (remaining > bytesToInspect) {
          dup.position(dup.position() + (remaining - bytesToInspect))
        }
        if (scratchBytes.size < bytesToInspect) {
          scratchBytes = ByteArray(bytesToInspect)
        }
        dup.get(scratchBytes, 0, bytesToInspect)
        val frame = analyzer.analyzePcm16(scratchBytes, bytesToInspect, sampleRate, channelCount)
        onSpectrumFrame(frame)
      }
    }

    outputBuffer.put(inputBuffer)
    outputBuffer.flip()
  }

  override fun getOutput(): ByteBuffer {
    val buffer = outputBuffer
    outputBuffer = EMPTY_BUFFER
    return buffer
  }

  override fun isEnded(): Boolean = inputEnded && outputBuffer.remaining() == 0

  @Deprecated("Deprecated in Java")
  override fun flush() {
    outputBuffer = EMPTY_BUFFER
    inputEnded = false
  }

  override fun reset() {
    @Suppress("DEPRECATION") flush()
    sampleRate = 44100
    channelCount = 2
    encoding = C.ENCODING_INVALID
    isActive = false
    onSpectrumFrame(analyzer.reset())
  }

  override fun queueEndOfStream() {
    inputEnded = true
  }

  private companion object {
    val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
  }
}
