package redux.optimist

import redux.optimist.Action.Optimist.Status
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

abstract class Action {

    private val optimist = Optimist()

    fun id() = optimist.id
    fun status(): Status = optimist.status.get()
    fun success() = apply { optimist.status.set(Status.SUCCESS) }
    fun failure() = apply { optimist.status.set(Status.FAILURE) }
    fun resolved() = apply { optimist.status.set(Status.RESOLVED) }

    data class Optimist(
        val id: Any = UUID.randomUUID(),
        val status: AtomicReference<Status> = AtomicReference(Status.PENDING)) {

        enum class Status {
            PENDING, SUCCESS, FAILURE, RESOLVED
        }

    }

}
