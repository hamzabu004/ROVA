package rova

import kotlin.test.*

class PatrolTest {
    private val map = CorridorMap(1, listOf(Room("lab_3", "Lab 3")))
    private val result = Inspection(Door.Open, Occupancy.NoOneVisible, 0, false, Visibility.On, Visibility.Off)
    private fun ready(blur: Boolean = true): Pair<Patrol, SimulatedEsp32> {
        val esp = SimulatedEsp32(); val p = Patrol(esp)
        p.start(Assignment("p", "s", "lab_3", 1, 1, blur), map)
        p.motionComplete(esp.commands.last().id, 1)
        return p to esp
    }
    private fun plate(p: Patrol, text: String = "Lab 3", privacy: String = "blurred") =
        p.plateResult(1, "s", Frame("f", "fixture://door", privacy), text)
    @Test fun correctStopReturnsHomeWithOneObservation() {
        val (p, esp) = ready(); plate(p); p.inspectionResult(1, "s", result)
        p.inspectionResult(1, "s", result)
        assertEquals(1, p.observations.size)
        assertEquals("lab_3", p.observations.single().verifiedRoomId)
        assertEquals("replay", p.observations.single().executionMode)
        p.motionComplete(esp.commands.last().id, 1); assertEquals(State.Completed, p.state)
    }
    @Test fun mismatchCannotRecordInspection() {
        val (p, _) = ready(); plate(p, "Lab 2"); p.inspectionResult(1, "s", result)
        assertEquals(State.UncertainLocation, p.state); assertTrue(p.observations.isEmpty())
        p.skip(); assertEquals(State.ReturningHome, p.state)
    }
    @Test fun closedDoorCannotClaimOccupancy() {
        val (p, _) = ready(); plate(p)
        assertFailsWith<IllegalArgumentException> { p.inspectionResult(1, "s", result.copy(door = Door.Closed)) }
        p.inspectionResult(1, "s", result.copy(door = Door.Closed, occupancy = Occupancy.CannotDetermine, count = null))
        assertEquals(Occupancy.CannotDetermine, p.observations.single().inspection.occupancy)
    }
    @Test fun partialViewCannotClaimEmpty() {
        assertFailsWith<IllegalArgumentException> { result.copy(occupancy = Occupancy.Empty).validated() }
    }
    @Test fun takeoverDiscardsLatePerceptionAndMotion() {
        val (p, esp) = ready(); plate(p); val old = esp.commands.first()
        p.takeover(2); p.inspectionResult(1, "s", result); p.motionComplete(old.id, 1)
        assertEquals(State.ManualTakeover, p.state); assertTrue(p.observations.isEmpty())
        assertEquals("cancel_motion", esp.commands.last().action)
    }
    @Test fun offAllowsUnblurredAndOnRejectsIt() {
        val (on, _) = ready(); assertFailsWith<IllegalArgumentException> { plate(on, privacy = "unblurred") }
        val (off, _) = ready(false); plate(off, privacy = "unblurred"); off.inspectionResult(1, "s", result)
        assertFalse(off.observations.single().assignment.faceBlurEnabled)
        assertEquals("unblurred", off.observations.single().frame.privacyApplied)
    }
    @Test fun wrongMapFailsWithoutMotion() {
        val esp = SimulatedEsp32(); val p = Patrol(esp)
        p.start(Assignment("p", "s", "lab_3", 2, 1), map)
        assertEquals(State.Failed, p.state); assertTrue(esp.commands.isEmpty())
    }
    @Test fun cancelStopsAndDiscardsLateResult() {
        val (p, esp) = ready(); plate(p); p.cancel(); p.inspectionResult(1, "s", result)
        assertEquals(State.Cancelled, p.state); assertTrue(p.observations.isEmpty())
        assertEquals("cancel_motion", esp.commands.last().action)
    }
    @Test fun wrongStopOrSessionCannotChangeState() {
        val (p, _) = ready(); plate(p)
        p.inspectionResult(1, "other", result); p.inspectionResult(2, "s", result)
        assertEquals(State.InspectingDoor, p.state); assertTrue(p.observations.isEmpty())
    }
    @Test fun duplicateOrWrongMotionCompletionCannotSkipPlateReading() {
        val esp = SimulatedEsp32(); val p = Patrol(esp)
        p.start(Assignment("p", "s", "lab_3", 1, 1), map)
        val command = esp.commands.single()
        p.motionComplete("other", 1); p.motionComplete(command.id, 2)
        assertEquals(State.MovingToDoor, p.state)
        p.motionComplete(command.id, 1); p.motionComplete(command.id, 1)
        assertEquals(State.ReadingPlate, p.state)
        assertTrue(p.observations.isEmpty())
    }
    @Test fun simulatorDeduplicatesRetriedCommands() {
        val esp = SimulatedEsp32(); val move = Motion("cmd", 1, "move_edge")
        esp.send(move); esp.send(move)
        assertEquals(listOf(move), esp.commands)
    }
}
