package keswa.domain

/** Holds the currently unlocked [Principal] for this process. Set on PIN unlock, cleared on lock. */
interface PrincipalHolder {
    fun current(): Principal?
    fun set(principal: Principal?)
}

/** Screens reachable only after unlock can assume a principal exists; fail loudly if that invariant breaks. */
fun PrincipalHolder.require(): Principal =
    current() ?: error("No principal set — this screen is only reachable after PIN unlock.")

class InMemoryPrincipalHolder : PrincipalHolder {
    private var principal: Principal? = null
    override fun current(): Principal? = principal
    override fun set(principal: Principal?) { this.principal = principal }
}
