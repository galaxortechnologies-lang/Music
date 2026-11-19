package echo.music.iad1tya.api

import com.nigutu.app.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * Configuration and safe availability guard for the optional Flow Neuroengine integration.
 *
 * When `FLOW_NEURO_API_KEY` is blank, contains a known placeholder, or `FLOW_NEURO_BASE_URL`
 * is missing/invalid, the integration is cleanly disabled without affecting playback,
 * offline recommendations, or APK builds.
 */
object FlowNeuroConfig {
  private val PLACEHOLDER_KEYS =
    setOf("dummy_key", "your_api_key", "changeme", "placeholder", "none", "null")

  fun isConfigured(
    apiKey: String = BuildConfig.FLOW_NEURO_API_KEY,
    baseUrl: String = BuildConfig.FLOW_NEURO_BASE_URL,
  ): Boolean {
    val trimmedKey = apiKey.trim()
    val trimmedUrl = baseUrl.trim()
    if (trimmedKey.isEmpty() || trimmedKey.lowercase() in PLACEHOLDER_KEYS) {
      return false
    }
    return trimmedUrl.startsWith("https://", ignoreCase = true) ||
      trimmedUrl.startsWith("http://", ignoreCase = true)
  }

  val isEnabled: Boolean
    get() = isConfigured()

  val apiKeyOrNull: String?
    get() = BuildConfig.FLOW_NEURO_API_KEY.trim().takeIf { isConfigured(apiKey = it) }

  val baseUrlOrNull: String?
    get() {
      val trimmed = BuildConfig.FLOW_NEURO_BASE_URL.trim()
      if (!isConfigured(baseUrl = trimmed)) return null
      return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

  fun createApiOrNull(okHttpClient: OkHttpClient): FlowNeuroengineApi? {
    val baseUrl = baseUrlOrNull ?: return null
    val apiKey = apiKeyOrNull ?: return null
    val authenticatedClient =
      okHttpClient
        .newBuilder()
        .addInterceptor { chain ->
          val request =
            chain
              .request()
              .newBuilder()
              .header("Authorization", "Bearer $apiKey")
              .build()
          chain.proceed(request)
        }
        .build()

    return Retrofit.Builder()
      .baseUrl(baseUrl)
      .client(authenticatedClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()
      .create(FlowNeuroengineApi::class.java)
  }
}
