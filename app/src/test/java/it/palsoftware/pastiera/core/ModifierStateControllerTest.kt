package it.palsoftware.pastiera.core

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ModifierStateControllerTest {

    private val doubleTapThreshold = 300L

    @Test
    fun testShiftTransitions() {
        val controller = ModifierStateController(doubleTapThreshold)
        
        // Initial state: OFF
        assertEquals(ShiftState.OFF, controller.shiftState)
        
        // 1. OFF -> ONE_SHOT
        controller.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.ONE_SHOT, controller.shiftState)
        assertTrue(controller.shiftPressed)
        assertTrue(controller.shiftPhysicallyPressed)
        
        controller.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.ONE_SHOT, controller.shiftState)
        assertFalse(controller.shiftPressed)
        assertFalse(controller.shiftPhysicallyPressed)
        
        // 2. ONE_SHOT -> OFF (if tap again after threshold or non-consecutive)
        // Simulate waiting longer than threshold (or just non-consecutive tap)
        // Here we test non-consecutive by using a different key in between
        controller.registerNonModifierKey()
        controller.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.OFF, controller.shiftState)
        controller.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
    }

    @Test
    fun testCapsLockTransition() {
        val controller = ModifierStateController(doubleTapThreshold)
        
        // OFF -> ONE_SHOT
        controller.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        controller.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.ONE_SHOT, controller.shiftState)
        
        // ONE_SHOT -> CAPS (Double tap / Consecutive tap)
        // Since we are in the same test and no other keys pressed, it should be consecutive
        controller.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.CAPS, controller.shiftState)
        assertTrue(controller.capsLockEnabled)
        
        controller.handleShiftKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.CAPS, controller.shiftState)
        
        // CAPS -> OFF (Tap during CAPS)
        controller.handleShiftKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertFalse(controller.capsLockEnabled)
    }

    @Test
    fun testConsumeOneShot() {
        val controller = ModifierStateController(doubleTapThreshold)
        
        // Set ONE_SHOT
        controller.shiftOneShot = true
        assertEquals(ShiftState.ONE_SHOT, controller.shiftState)
        
        // Consume it
        assertTrue(controller.consumeShiftOneShot())
        assertEquals(ShiftState.OFF, controller.shiftState)
        
        // Consume again (should be false)
        assertFalse(controller.consumeShiftOneShot())
    }

    @Test
    fun testCapsLockStaysActive() {
        val controller = ModifierStateController(doubleTapThreshold)
        
        // Enable CAPS_LOCK
        controller.capsLockEnabled = true
        assertEquals(ShiftState.CAPS, controller.shiftState)
        
        // Simulate typing letters and spaces
        controller.registerNonModifierKey()
        assertFalse(controller.consumeShiftOneShot()) // CAPS shouldn't be consumed by ONE_SHOT consumer
        assertEquals(ShiftState.CAPS, controller.shiftState)
        
        controller.registerNonModifierKey()
        assertEquals(ShiftState.CAPS, controller.shiftState)
        
        // Reset via space logic usually happens in the InputEventRouter, 
        // but let's see if the controller itself stays in CAPS
        controller.registerNonModifierKey()
        assertEquals(ShiftState.CAPS, controller.shiftState)
    }
    
    @Test
    fun testResetModifiers() {
        val controller = ModifierStateController(doubleTapThreshold)
        
        controller.capsLockEnabled = true
        controller.ctrlLatchActive = true
        
        var navModeCancelled = false
        controller.resetModifiers(preserveNavMode = false) {
            navModeCancelled = true
        }
        
        assertEquals(ShiftState.OFF, controller.shiftState)
        assertFalse(controller.ctrlLatchActive)
        assertTrue(navModeCancelled)
    }
}

