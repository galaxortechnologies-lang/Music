package com.music.echo.ui.player

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.nigutu.app.R
import echo.music.dsp.core.SpectrumFrame
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Renders live `:dsp-core` FFT frequency spectrum data using bundled offline D3 v7 inside a
 * hardened, zero-network [WebView] with DSP band telemetry readouts.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DspSpectrumVisualizerCard(
  spectrumFrame: SpectrumFrame,
  isPlaying: Boolean,
  accentColor: Color,
  contentColor: Color,
  onClose: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val accentHex = remember(accentColor) {
    val rgb = accentColor.toArgb() and 0x00FFFFFF
    "#${rgb.toString(16).padStart(6, '0')}"
  }

  val bundledHtml by
    produceState<String?>(initialValue = null, key1 = context) {
      value =
        withContext(Dispatchers.IO) {
          runCatching {
            val d3Script =
              context.assets.open("spectrum/d3.v7.min.js").bufferedReader().use { it.readText() }
            val template =
              context.assets.open("spectrum/spectrum.html").bufferedReader().use { it.readText() }
            template.replace("<!-- D3_INLINE_PLACEHOLDER -->", "<script>$d3Script</script>")
          }.getOrNull()
        }
    }

  var webViewRef by remember { mutableStateOf<WebView?>(null) }

  LaunchedEffect(webViewRef, spectrumFrame, accentHex) {
    val webView = webViewRef ?: return@LaunchedEffect
    val json = spectrumFrame.toJsonString()
    webView.evaluateJavascript("if(window.updateSpectrum){window.updateSpectrum($json);}", null)
  }

  LaunchedEffect(webViewRef, accentHex) {
    val webView = webViewRef ?: return@LaunchedEffect
    webView.evaluateJavascript(
      "if(window.setSpectrumAccent){window.setSpectrumAccent('$accentHex');}",
      null,
    )
  }

  DisposableEffect(Unit) {
    onDispose {
      webViewRef?.let { wv ->
        runCatching {
          wv.stopLoading()
          wv.destroy()
        }
      }
      webViewRef = null
    }
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = Color.Black.copy(alpha = 0.28f),
    border = BorderStroke(0.75.dp, contentColor.copy(alpha = 0.16f)),
    modifier = modifier.fillMaxWidth().testTag("spectrum_visualizer_card"),
  ) {
    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Icon(
            painter = painterResource(R.drawable.graphic_eq),
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(16.dp),
          )
          Text(
            text =
              if (isPlaying) {
                "DSP Spectrum • ${spectrumFrame.sampleRateHz / 1000.0}kHz"
              } else {
                "DSP Spectrum • Paused"
              },
            style =
              MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
              ),
            color = contentColor.copy(alpha = 0.9f),
          )
        }

        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          TelemetryBadge("B", spectrumFrame.bassEnergy, contentColor)
          TelemetryBadge("M", spectrumFrame.midEnergy, contentColor)
          TelemetryBadge("H", spectrumFrame.highEnergy, contentColor)
          IconButton(
            onClick = onClose,
            modifier = Modifier.size(28.dp).testTag("close_spectrum_button"),
          ) {
            Icon(
              painter = painterResource(R.drawable.close),
              contentDescription = "Hide spectrum",
              tint = contentColor.copy(alpha = 0.72f),
              modifier = Modifier.size(16.dp),
            )
          }
        }
      }

      Box(
        modifier =
          Modifier.fillMaxWidth()
            .height(92.dp)
            .padding(top = 6.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.2f))
      ) {
        val html = bundledHtml
        if (html != null) {
          AndroidView(
            factory = { ctx ->
              WebView(ctx).apply {
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                settings.apply {
                  javaScriptEnabled = true
                  domStorageEnabled = false
                  allowFileAccess = false
                  allowContentAccess = false
                  blockNetworkLoads = true
                  cacheMode = WebSettings.LOAD_NO_CACHE
                  setSupportZoom(false)
                }
                loadDataWithBaseURL("https://nigutu.app.local/", html, "text/html", "UTF-8", null)
                webViewRef = this
              }
            },
            modifier = Modifier.fillMaxWidth().height(92.dp).testTag("spectrum_d3_webview"),
          )
        }
      }
    }
  }
}

@Composable
private fun TelemetryBadge(label: String, energy: Float, contentColor: Color) {
  val pct = (energy.coerceIn(0f, 1f) * 100f).roundToInt()
  Text(
    text = "$label $pct%",
    style =
      MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
      ),
    color = contentColor.copy(alpha = 0.75f),
  )
}
