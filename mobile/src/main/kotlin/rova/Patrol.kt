package rova

enum class State { Idle, SyncingMap, MovingToDoor, ArrivedEstimate, ReadingPlate,
    VerifyingPlace, UncertainLocation, InspectingDoor, RecordingEvidence,
    NextOrHome, ReturningHome, ManualTakeover, Completed, Cancelled, Failed }
enum class Occupancy { Occupied, NoOneVisible, Empty, CannotDetermine }
enum class Door { Open, Closed, Ajar, CannotDetermine }
enum class Visibility { On, Off, CannotDetermine }
data class Room(val id: String, val plate: String)
data class CorridorMap(val version: Int, val rooms: List<Room>)
data class Assignment(val patrolId: String, val stopId: String, val roomId: String,
    val mapVersion: Int, val session: Long, val faceBlurEnabled: Boolean = true)
data class Frame(val id: String, val evidenceRef: String, val privacyApplied: String)
data class Inspection(val door: Door, val occupancy: Occupancy, val count: Int?,
    val wholeRoomVisible: Boolean, val lights: Visibility, val projector: Visibility) {
    fun validated(): Inspection {
        require(count == null || count >= 0)
        require(occupancy != Occupancy.Empty || (wholeRoomVisible && count == 0))
        require(occupancy != Occupancy.NoOneVisible || count == 0)
        require(occupancy != Occupancy.Occupied || (count != null && count > 0))
        require(occupancy != Occupancy.CannotDetermine || count == null)
        require(door != Door.Closed || (occupancy == Occupancy.CannotDetermine && count == null))
        return this
    }
}
data class Observation(val assignment: Assignment, val verifiedRoomId: String,
    val frame: Frame, val inspection: Inspection,
    val executionMode: String = "replay", val perceptionProvider: String = "recorded")
data class Motion(val id: String, val session: Long, val action: String)

/** Commands only: transport callbacks must be returned to the coordinator by its caller. */
interface Esp32Transport { fun send(command: Motion) }
class SimulatedEsp32 : Esp32Transport {
    private val seen = mutableSetOf<String>()
    val commands = mutableListOf<Motion>()
    override fun send(command: Motion) { if (seen.add(command.id)) commands.add(command) }
}

/** Single-stop deterministic core. Call from one serialized event loop on Android. */
class Patrol(private val transport: Esp32Transport) {
    var state = State.Idle; private set
    var session = 0L; private set
    val history: List<State> get() = transitions.toList()
    val observations: List<Observation> get() = stored.toList()
    private val transitions = mutableListOf(State.Idle)
    private val stored = mutableListOf<Observation>()
    private lateinit var assignment: Assignment
    private lateinit var room: Room
    private var frame: Frame? = null
    private var pendingMotion: Motion? = null
    private var commandNumber = 0
    private fun enter(next: State) { state = next; transitions.add(next) }
    private fun motion(action: String) {
        val command = Motion("${assignment.patrolId}:${++commandNumber}", session, action)
        pendingMotion = command
        transport.send(command)
    }
    fun start(task: Assignment, map: CorridorMap) {
        check(state == State.Idle)
        require(task.patrolId.isNotBlank() && task.stopId.isNotBlank() && task.session > 0)
        assignment = task.copy(); session = task.session
        enter(State.SyncingMap)
        val candidate = map.rooms.singleOrNull { it.id == task.roomId }
        if (map.version != task.mapVersion || candidate == null) { enter(State.Failed); return }
        room = candidate.copy()
        enter(State.MovingToDoor); motion("move_edge")
    }
    fun motionComplete(commandId: String, responseSession: Long) {
        val pending = pendingMotion ?: return
        if (pending.id != commandId || responseSession != session || pending.session != session) return
        when (state) {
            State.MovingToDoor -> { pendingMotion = null; enter(State.ArrivedEstimate); enter(State.ReadingPlate) }
            State.ReturningHome -> { pendingMotion = null; enter(State.Completed) }
            else -> Unit
        }
    }
    fun plateResult(responseSession: Long, stopId: String, image: Frame, plate: String?) {
        if (!accept(responseSession, stopId, State.ReadingPlate)) return
        require(image.id.isNotBlank() && image.evidenceRef.isNotBlank())
        val expected = if (assignment.faceBlurEnabled) "blurred" else "unblurred"
        require(image.privacyApplied == expected) { "Frame violates pinned blur policy" }
        frame = image.copy(); enter(State.VerifyingPlace)
        enter(if (plate != null && normalize(plate) == normalize(room.plate)) State.InspectingDoor else State.UncertainLocation)
    }
    fun confirmRoom(roomId: String) {
        check(state == State.UncertainLocation); require(roomId == room.id)
        enter(State.InspectingDoor)
    }
    fun recheck() { check(state == State.UncertainLocation); frame = null; enter(State.ReadingPlate) }
    fun skip() { check(state == State.UncertainLocation); returnHome() }
    fun inspectionResult(responseSession: Long, stopId: String, result: Inspection) {
        if (!accept(responseSession, stopId, State.InspectingDoor)) return
        result.validated()
        enter(State.RecordingEvidence)
        stored.add(Observation(assignment.copy(session = session), room.id, checkNotNull(frame), result))
        returnHome()
    }
    private fun returnHome() { enter(State.NextOrHome); enter(State.ReturningHome); motion("return_home") }
    fun takeover(nextSession: Long) {
        check(active()); require(nextSession > session)
        session = nextSession; pendingMotion = null
        enter(State.ManualTakeover); motion("cancel_motion")
    }
    fun cancel() {
        check(active()); pendingMotion = null
        enter(State.Cancelled); motion("cancel_motion")
    }
    private fun active() = state !in setOf(State.Idle, State.Completed, State.Cancelled, State.Failed)
    private fun accept(s: Long, stop: String, expected: State) =
        state == expected && session == s && assignment.stopId == stop
    private fun normalize(text: String) = text.trim().lowercase().replace(Regex("\\s+"), " ")
}
