package com.skydream.androiddesk.core

import com.skydream.androiddesk.core.control.ConfirmDecision
import com.skydream.androiddesk.core.control.ConfirmGate
import com.skydream.androiddesk.core.control.ConfirmPolicy
import com.skydream.androiddesk.core.control.ConfirmTier
import com.skydream.androiddesk.core.control.DesktopActionKind
import kotlin.test.Test
import kotlin.test.assertEquals

class ConfirmGateTest {
    @Test
    fun alwaysAskBeforeClicking() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AlwaysAsk))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.Click))
    }

    @Test
    fun askOncePerTaskStillAsksForPaymentEveryTime() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AskOncePerTask))
        gate.markTaskConfirmed("t1")
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.Click))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.Payment))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.Send))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.Delete))
    }

    @Test
    fun passwordAndOtpAreHandedBackByDefaultEvenIfNeverAsk() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.NeverAsk))
        assertEquals(ConfirmDecision.HandBackToUser, gate.decide("t1", DesktopActionKind.EnterPassword))
        assertEquals(ConfirmDecision.HandBackToUser, gate.decide("t1", DesktopActionKind.EnterOtp))
        assertEquals(ConfirmDecision.HandBackToUser, gate.decide("t1", DesktopActionKind.OpenSystemSettings))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.Click))
    }

    @Test
    fun stopIsAlwaysAllowed() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AlwaysAsk))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.Stop))
    }
}
