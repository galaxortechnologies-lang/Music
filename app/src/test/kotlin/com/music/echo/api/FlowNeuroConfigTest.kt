package echo.music.iad1tya.api

import okhttp3.OkHttpClient
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowNeuroConfigTest {

  @Test
  fun emptyApiKeyDisablesFlowNeuro() {
    assertFalse(FlowNeuroConfig.isConfigured(apiKey = "", baseUrl = "https://api.flowneuroengine.com"))
    assertFalse(FlowNeuroConfig.isConfigured(apiKey = "   ", baseUrl = "https://api.flowneuroengine.com"))
  }

  @Test
  fun placeholderApiKeyDisablesFlowNeuro() {
    assertFalse(
      FlowNeuroConfig.isConfigured(apiKey = "dummy_key", baseUrl = "https://api.flowneuroengine.com")
    )
    assertFalse(
      FlowNeuroConfig.isConfigured(apiKey = "YOUR_API_KEY", baseUrl = "https://api.flowneuroengine.com")
    )
  }

  @Test
  fun missingOrInvalidBaseUrlDisablesFlowNeuro() {
    assertFalse(FlowNeuroConfig.isConfigured(apiKey = "real-secret-token", baseUrl = ""))
    assertFalse(FlowNeuroConfig.isConfigured(apiKey = "real-secret-token", baseUrl = "ftp://example.com"))
  }

  @Test
  fun validKeyAndHttpsUrlEnablesFlowNeuro() {
    assertTrue(
      FlowNeuroConfig.isConfigured(
        apiKey = "fn_live_test_token_123",
        baseUrl = "https://api.flowneuroengine.com",
      )
    )
  }

  @Test
  fun createApiOrNullReturnsNullWhenBuildConfigKeyIsMissing() {
    if (!FlowNeuroConfig.isEnabled) {
      assertNull(FlowNeuroConfig.createApiOrNull(OkHttpClient()))
    }
  }
}
