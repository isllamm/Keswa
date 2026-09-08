package keswa.domain

/** Holds the currently unlocked [Principal] for this process. Set on PIN unlock, cleared on lock. */
interface PrincipalHolder {
    fun current(): Principal?
    fun set(principal: Principal?)
}

class InMemoryPrincipalHolder : PrincipalHolder {
    private var principal: Principal? = null
    override fun current(): Principal? = principal
    override fun set(principal: Principal?) { this.principal = principal }
}
