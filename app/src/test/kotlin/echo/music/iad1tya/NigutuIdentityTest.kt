package echo.music.iad1tya

import com.nigutu.app.BuildConfig
import com.nigutu.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NigutuIdentityTest {

  @Test
  fun applicationIdAndAppNameMatchNigutu() {
    assertTrue(BuildConfig.APPLICATION_ID.startsWith("com.nigutu.app"))
    val context = RuntimeEnvironment.getApplication()
    assertEquals("Nigutu", context.getString(R.string.app_name))
  }
}
