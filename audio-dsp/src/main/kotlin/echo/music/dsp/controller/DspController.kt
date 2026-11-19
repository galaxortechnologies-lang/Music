package echo.music.dsp.controller

import androidx.media3.common.audio.AudioProcessor
import echo.music.dsp.audio.AutomixDuckAudioProcessor
import echo.music.dsp.audio.CustomEqualizerAudioProcessor
import echo.music.dsp.audio.SpectrumAudioProcessor
import echo.music.dsp.audio.StereoWidenerAudioProcessor
import echo.music.dsp.core.SpectrumFrame
import echo.music.dsp.core.models.SavedEQProfile
import kotlinx.coroutines.flow.StateFlow

data class PlayerDspProcessors(
  val equalizer: CustomEqualizerAudioProcessor,
  val duckProcessor: AutomixDuckAudioProcessor,
  val stereoWidener: StereoWidenerAudioProcessor,
  val spectrumProcessor: SpectrumAudioProcessor? = null,
) {
  fun asList(): List<AudioProcessor> =
    listOfNotNull(equalizer, duckProcessor, stereoWidener, spectrumProcessor)
}

interface DspController {
  val activeProfile: StateFlow<SavedEQProfile?>
  val isEnabled: StateFlow<Boolean>
  val stereoWidth: StateFlow<Float>
  val spectrumFrame: StateFlow<SpectrumFrame>

  /** Returns list of all configured DSP processors for ExoPlayer audio chain */
  fun getAudioProcessors(): List<AudioProcessor>

  /** Factory for creating and registering a synchronized quartet of processors for an ExoPlayer pipeline */
  fun createProcessors(): PlayerDspProcessors

  /** Unregisters processors when an ExoPlayer instance is released */
  fun releaseProcessors(processors: PlayerDspProcessors)

  fun addAudioProcessor(processor: CustomEqualizerAudioProcessor)
  fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor)

  fun addStereoWidener(processor: StereoWidenerAudioProcessor)
  fun removeStereoWidener(processor: StereoWidenerAudioProcessor)

  fun addDuckProcessor(processor: AutomixDuckAudioProcessor)
  fun removeDuckProcessor(processor: AutomixDuckAudioProcessor)

  fun addSpectrumProcessor(processor: SpectrumAudioProcessor) {}
  fun removeSpectrumProcessor(processor: SpectrumAudioProcessor) {}
  fun createSpectrumProcessor(): SpectrumAudioProcessor
  fun setSpectrumEnabled(enabled: Boolean) {}
  fun resetSpectrum() {}

  fun applyProfile(profile: SavedEQProfile): Result<Unit>
  fun disable()
  fun disableEqualizer()
  fun setStereoWidth(width: Float)
  fun setAutoMixDuck(duckFactor: Float)
  fun isInitialized(): Boolean
  fun release()
}
