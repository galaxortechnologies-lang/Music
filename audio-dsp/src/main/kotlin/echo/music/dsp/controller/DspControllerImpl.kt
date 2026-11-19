package echo.music.dsp.controller

import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import echo.music.dsp.audio.AutomixDuckAudioProcessor
import echo.music.dsp.audio.CustomEqualizerAudioProcessor
import echo.music.dsp.audio.SpectrumAudioProcessor
import echo.music.dsp.audio.StereoWidenerAudioProcessor
import echo.music.dsp.core.SpectrumAnalyzer
import echo.music.dsp.core.SpectrumFrame
import echo.music.dsp.core.models.SavedEQProfile
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

@Singleton
@OptIn(UnstableApi::class)
class DspControllerImpl @Inject constructor() : DspController {

  private val eqProcessors = CopyOnWriteArrayList<CustomEqualizerAudioProcessor>()
  private val widenerProcessors = CopyOnWriteArrayList<StereoWidenerAudioProcessor>()
  private val duckProcessors = CopyOnWriteArrayList<AutomixDuckAudioProcessor>()
  private val spectrumProcessors = CopyOnWriteArrayList<SpectrumAudioProcessor>()

  private val _activeProfile = MutableStateFlow<SavedEQProfile?>(null)
  override val activeProfile: StateFlow<SavedEQProfile?> = _activeProfile.asStateFlow()

  private val _isEnabled = MutableStateFlow(false)
  override val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

  private val _stereoWidth = MutableStateFlow(1f)
  override val stereoWidth: StateFlow<Float> = _stereoWidth.asStateFlow()

  private val _spectrumFrame = MutableStateFlow(SpectrumFrame.EMPTY)
  override val spectrumFrame: StateFlow<SpectrumFrame> = _spectrumFrame.asStateFlow()

  @Volatile private var spectrumEnabled: Boolean = false
  @Volatile private var pendingProfile: SavedEQProfile? = null
  @Volatile private var shouldDisable: Boolean = false

  companion object {
    private const val TAG = "DspController"
  }

  override fun getAudioProcessors(): List<AudioProcessor> {
    if (eqProcessors.isEmpty() && widenerProcessors.isEmpty() && duckProcessors.isEmpty()) {
      return emptyList()
    }
    val processors = mutableListOf<AudioProcessor>()
    eqProcessors.firstOrNull()?.let { processors.add(it) }
    duckProcessors.firstOrNull()?.let { processors.add(it) }
    widenerProcessors.firstOrNull()?.let { processors.add(it) }
    return processors
  }

  override fun createProcessors(): PlayerDspProcessors {
    val eq = CustomEqualizerAudioProcessor()
    val duck = AutomixDuckAudioProcessor()
    val widener = StereoWidenerAudioProcessor().apply { width = _stereoWidth.value }
    val spectrum = createSpectrumProcessor()

    addAudioProcessor(eq)
    addDuckProcessor(duck)
    addStereoWidener(widener)
    addSpectrumProcessor(spectrum)

    return PlayerDspProcessors(
      equalizer = eq,
      duckProcessor = duck,
      stereoWidener = widener,
      spectrumProcessor = spectrum,
    )
  }

  override fun createSpectrumProcessor(): SpectrumAudioProcessor {
    return SpectrumAudioProcessor(analyzer = SpectrumAnalyzer()) { frame ->
        _spectrumFrame.value = frame
      }
      .apply { analysisEnabled = spectrumEnabled }
  }

  override fun releaseProcessors(processors: PlayerDspProcessors) {
    removeAudioProcessor(processors.equalizer)
    removeDuckProcessor(processors.duckProcessor)
    removeStereoWidener(processors.stereoWidener)
    processors.spectrumProcessor?.let { removeSpectrumProcessor(it) }
  }

  override fun addAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    eqProcessors.add(processor)
    Timber.tag(TAG).d("CustomEqualizerAudioProcessor added. Total: ${eqProcessors.size}")

    if (shouldDisable) {
      processor.disable()
    } else if (pendingProfile != null) {
      processor.applyProfile(pendingProfile!!.parametricEQ)
    }
  }

  override fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor) {
    eqProcessors.remove(processor)
  }

  override fun addStereoWidener(processor: StereoWidenerAudioProcessor) {
    processor.width = _stereoWidth.value
    widenerProcessors.add(processor)
  }

  override fun removeStereoWidener(processor: StereoWidenerAudioProcessor) {
    widenerProcessors.remove(processor)
  }

  override fun addDuckProcessor(processor: AutomixDuckAudioProcessor) {
    duckProcessors.add(processor)
  }

  override fun removeDuckProcessor(processor: AutomixDuckAudioProcessor) {
    duckProcessors.remove(processor)
  }

  override fun addSpectrumProcessor(processor: SpectrumAudioProcessor) {
    processor.analysisEnabled = spectrumEnabled
    spectrumProcessors.add(processor)
  }

  override fun removeSpectrumProcessor(processor: SpectrumAudioProcessor) {
    spectrumProcessors.remove(processor)
    if (spectrumProcessors.isEmpty()) {
      _spectrumFrame.value = SpectrumFrame.EMPTY
    }
  }

  override fun setSpectrumEnabled(enabled: Boolean) {
    spectrumEnabled = enabled
    spectrumProcessors.forEach { it.analysisEnabled = enabled }
    if (!enabled) {
      _spectrumFrame.value = SpectrumFrame.EMPTY
    }
  }

  override fun resetSpectrum() {
    _spectrumFrame.value = SpectrumFrame.EMPTY
  }

  override fun applyProfile(profile: SavedEQProfile): Result<Unit> {
    pendingProfile = profile
    shouldDisable = false
    _activeProfile.value = profile
    _isEnabled.value = true

    if (eqProcessors.isEmpty()) {
      Timber.tag(TAG).w("No audio processors registered yet. Storing profile as pending: ${profile.name}")
      return Result.success(Unit)
    }

    var success = true
    var lastError: Exception? = null

    eqProcessors.forEach { processor ->
      try {
        processor.applyProfile(profile.parametricEQ)
      } catch (e: Exception) {
        success = false
        lastError = e
        Timber.tag(TAG).e(e, "Failed to apply profile to processor")
      }
    }

    return if (success) Result.success(Unit)
    else Result.failure(lastError ?: Exception("Unknown error applying EQ profile"))
  }

  override fun disable() {
    disableEqualizer()
  }

  override fun disableEqualizer() {
    shouldDisable = true
    pendingProfile = null
    _activeProfile.value = null
    _isEnabled.value = false

    if (eqProcessors.isEmpty()) {
      Timber.tag(TAG).w("No audio processors registered yet. Storing disable as pending")
      return
    }

    eqProcessors.forEach { processor ->
      try {
        processor.disable()
      } catch (e: Exception) {
        Timber.tag(TAG).e(e, "Failed to disable equalizer on processor")
      }
    }
    Timber.tag(TAG).d("Equalizer disabled on all processors")
  }

  override fun setStereoWidth(width: Float) {
    val clamped = width.coerceIn(1f, 2f)
    _stereoWidth.value = clamped
    widenerProcessors.forEach { it.width = clamped }
  }

  override fun setAutoMixDuck(duckFactor: Float) {
    val clamped = duckFactor.coerceIn(0f, 1f)
    duckProcessors.forEach { it.setMix(clamped) }
  }

  override fun isInitialized(): Boolean = eqProcessors.isNotEmpty()

  override fun release() {
    eqProcessors.clear()
    widenerProcessors.clear()
    duckProcessors.clear()
    spectrumProcessors.clear()
    _spectrumFrame.value = SpectrumFrame.EMPTY
    Timber.tag(TAG).d("All DSP processors released")
  }
}
