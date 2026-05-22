package com.lonquanzj.aireplaymate

import android.content.Intent
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchProbeTest {
    @Test(timeout = 15_000)
    fun mainActivity_launches_with_activityScenario() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(MainActivity.EXTRA_SMOKE_TEST_MODE, true)
        }

        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            var launched = false
            scenario.onActivity { activity ->
                launched = true
                Log.i("MainActivityLaunchProbe", "activity=${activity::class.java.simpleName}")
                assertFalse(activity.isFinishing)
                assertFalse(activity.isDestroyed)
            }

            assertTrue("ActivityScenario did not deliver activity callback", launched)
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}