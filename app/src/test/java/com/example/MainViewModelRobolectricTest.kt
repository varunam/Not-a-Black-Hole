package com.example

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.MainViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainViewModelRobolectricTest {

    private lateinit var app: Application

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("not_a_black_hole_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun firstLaunchShowsIntroOnceAndPersistsCompletion() {
        val firstLaunchVm = MainViewModel(app)
        assertTrue("Intro should show on first launch", firstLaunchVm.showIntro.value)

        firstLaunchVm.completeIntro()
        assertFalse("Intro should hide after completion", firstLaunchVm.showIntro.value)

        // Subsequent launch should not show intro automatically
        val secondLaunchVm = MainViewModel(app)
        assertFalse("Intro should remain hidden on subsequent launches", secondLaunchVm.showIntro.value)

        // Replaying from settings should reopen intro without resetting persistence
        secondLaunchVm.openIntroFromSettings()
        assertTrue("Intro should reopen when triggered from Settings", secondLaunchVm.showIntro.value)
    }

    @Test
    fun shareIntentBypassesIntroWithoutMarkingSeenIfFirstLaunch() {
        val vm = MainViewModel(app)
        assertTrue("Intro should initially be visible on first launch", vm.showIntro.value)

        vm.processUrl("invalid-not-a-url")
        assertFalse("External share/processUrl should dismiss intro overlay immediately", vm.showIntro.value)

        // Because user hasn't seen/completed the intro yet, next normal launch still shows it
        val nextVm = MainViewModel(app)
        assertTrue("First-time intro should still show on next normal launch if bypassed via Share", nextVm.showIntro.value)
    }
}
