package redux.optimist

import redux.Reducer
import redux.optimist.OptimistReducer.Action.Optimist.Status
import redux.optimist.OptimistReducer.Action.Optimist.Status.FAILURE
import redux.optimist.OptimistReducer.Action.Optimist.Status.PENDING
import redux.optimist.OptimistReducer.Action.Optimist.Status.RESOLVED
import redux.optimist.OptimistReducer.Action.Optimist.Status.SUCCESS
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/*
 * Copyright (C) 2016 Michael Pardo
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

class OptimistReducer<S : Any> : Reducer<S> {

    private val delegate: Reducer<S>

    private var actions: List<Any> = emptyList()
    private var storedState: S? = null

    private constructor(reducer: Reducer<S>) {
        delegate = reducer
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Override functions

    override fun reduce(state: S, action: Any): S {
        if (action is Action) {
            return when (action.status()) {
                PENDING -> reducePending(state, action)
                SUCCESS -> reduceSuccess(state, action)
                FAILURE -> reduceFailure(state, action)
                RESOLVED -> delegate.reduce(state, action)
            }
        }
        return delegate.reduce(state, action)
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Private functions

    private fun reducePending(state: S, action: Action): S {
        // Store pre-action state if queue is empty
        if (actions.isEmpty()) storedState = state
        // Add action to queue
        actions += action
        // Continue with optimistic reduction
        return delegate.reduce(state, action)
    }

    private fun reduceSuccess(state: S, action: Action): S {
        // Find the action by id action and resolve it
        actions = actions.map { if (it is Action && it.id() == action.id()) it.resolved() else it }
        // Collect all actions until an unresolved optimistic action is found
        val resolvedActions = actions.takeWhile { it !is Action || it.status() == RESOLVED }
        // Remove resolved actions from action queue
        actions -= resolvedActions
        // Reduce the stored state with the resolved actions, or set as null if action queue is empty
        storedState = actions.emptyAsNull()?.fold(storedState) { state, action ->
            state?.let { delegate.reduce(state, action) }
        }
        // Optimistic state is still valid, return it.
        return state
    }

    private fun reduceFailure(state: S, action: Action): S {
        // Remove the failed action
        actions = actions.filter { it is Action && it.id() != action.id() }
        // Collect all actions until an unresolved optimistic action is found
        val resolvedActions = actions.takeWhile { it !is Action || it.status() == RESOLVED }
        // Remove resolved actions from action queue
        actions -= resolvedActions
        // Reduce the stored state with the resolved actions
        val newState = actions.fold(storedState!!) { state, action -> delegate.reduce(state, action) }
        // Clear the stored state if the action queue is empty
        if (actions.isEmpty()) storedState = null
        // Return newly calculated state
        return newState
    }

    // Utility function returns empty collection as null
    private fun <E> Collection<E>.emptyAsNull(): Collection<E>? = if (isEmpty()) null else this

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Public classes

    abstract class Action {

        private val optimist = Optimist()

        fun id() = optimist.id
        fun status() = optimist.status.get()
        fun success() = apply { optimist.status.set(SUCCESS) }
        fun failure() = apply { optimist.status.set(FAILURE) }
        fun resolved() = apply { optimist.status.set(RESOLVED) }

        data class Optimist(
                val id: Any = UUID.randomUUID(),
                val status: AtomicReference<Status> = AtomicReference(Status.PENDING)) {

            enum class Status {
                PENDING, SUCCESS, FAILURE, RESOLVED
            }
        }

    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Companion

    companion object {

        fun <S : Any> create(reducer: Reducer<S>): Reducer<S> = OptimistReducer(reducer)

    }

}
