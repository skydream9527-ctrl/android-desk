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

    @Test
    fun arrangingTheDesktopAsksButMovingAroundItDoesNot() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AlwaysAsk))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.ArrangeDesktop))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.LaunchApp))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.GoToPage))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.OpenDrawer))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.ReadDesktop))
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.ReportStatus))
    }

    @Test
    fun askOncePerTaskIsForgottenWhenTheTaskStops() {
        val gate = ConfirmGate(ConfirmPolicy(ConfirmTier.AskOncePerTask))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.ArrangeDesktop))
        gate.markTaskConfirmed("t1")
        assertEquals(ConfirmDecision.Allow, gate.decide("t1", DesktopActionKind.ArrangeDesktop))
        assertEquals(ConfirmDecision.AskUser, gate.decide("t2", DesktopActionKind.ArrangeDesktop))
        gate.stopTask("t1")
        assertEquals(ConfirmDecision.AskUser, gate.decide("t1", DesktopActionKind.ArrangeDesktop))
    }
}
