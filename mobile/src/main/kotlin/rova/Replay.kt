package rova

/** Synthetic fixtures only; no actual camera capture, face processing, network or motors. */
fun main(args: Array<String>) {
    val scenario = args.firstOrNull() ?: "correct"
    require(scenario in setOf("correct", "mismatch", "closed", "takeover", "blur-off"))
    val transport = SimulatedEsp32()
    val patrol = Patrol(transport)
    val task = Assignment("replay-1", "stop-1", "lab_3", 1, 1, scenario != "blur-off")
    patrol.start(task, CorridorMap(1, listOf(Room("lab_3", "Lab 3"))))
    patrol.motionComplete(transport.commands.last().id, 1)
    val privacy = if (task.faceBlurEnabled) "blurred" else "unblurred"
    patrol.plateResult(1, task.stopId, Frame("synthetic-$privacy", "fixture://doorway/$scenario", privacy),
        if (scenario == "mismatch") "Lab 2" else "Lab 3")
    val inspection = if (scenario == "closed")
        Inspection(Door.Closed, Occupancy.CannotDetermine, null, false, Visibility.CannotDetermine, Visibility.CannotDetermine)
    else Inspection(Door.Open, Occupancy.NoOneVisible, 0, false, Visibility.On, Visibility.Off)
    if (scenario == "takeover") patrol.takeover(2)
    patrol.inspectionResult(1, task.stopId, inspection)
    if (patrol.state == State.ReturningHome) patrol.motionComplete(transport.commands.last().id, 1)
    val expectedState = when (scenario) {
        "mismatch" -> State.UncertainLocation
        "takeover" -> State.ManualTakeover
        else -> State.Completed
    }
    check(patrol.state == expectedState) { "Unexpected replay outcome" }
    check(patrol.observations.size == if (expectedState == State.Completed) 1 else 0)
    println("REPLAY ONLY | scenario=$scenario | face_blur=${task.faceBlurEnabled}")
    println(patrol.history.joinToString(" -> "))
    patrol.observations.forEach { println(it) }
    println("Final state: ${patrol.state}; observations: ${patrol.observations.size}")
}
