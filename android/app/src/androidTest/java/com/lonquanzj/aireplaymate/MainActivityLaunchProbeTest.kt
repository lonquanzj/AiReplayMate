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
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchProbeTest {
    @Test(timeout = 12_000)
    fun mainActivity_launches_with_activityScenario() {
        val startedAt = System.currentTimeMillis()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        Log.i("MainActivityLaunchProbe", "launch begin")
        try {
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
            val elapsed = System.currentTimeMillis() - startedAt
            Log.i("MainActivityLaunchProbe", "launch success elapsed=${elapsed}ms")
        } catch (t: Throwable) {
            val elapsed = System.currentTimeMillis() - startedAt
            fail(
                "MainActivity launch probe failed after ${elapsed}ms; " +
                    "this diagnostic test can be blocked by OEM background-activity policies " +
                    "under instrumentation. cause=${t::class.java.simpleName}: ${t.message}"
            )
        }
    }
}