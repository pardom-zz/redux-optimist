package redux.optimist

import redux.api.Reducer
import redux.optimist.Action.Optimist.Status
import java.util.Collections.emptyList

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

fun <S : Any> createOptimistReducer(reducer: Reducer<S>): Reducer<S> {
    var actions: List<Any> = emptyList()
    var storedState: S? = null

    return Reducer { state, action ->

        fun <E> Collection<E>.emptyAsNull(): Collection<E>? = if (isEmpty()) null else this

        fun reducePending(state: S, action: Action): S {
            // Store pre-action state if queue is empty
            if (actions.isEmpty()) storedState = state
            // Add action to queue
            actions += action
            // Continue with optimistic reduction
            return reducer.reduce(state, action)
        }

        fun reduceSuccess(state: S, action: Action): S {
            // Find the action by id action and resolve it
            actions = actions.map { if (it is Action && it.id() == action.id()) it.resolved() else it }
            // Collect all actions until an unresolved optimistic action is found
            val resolvedActions = actions.takeWhile { it !is Action || it.status() == Status.RESOLVED }
            // Remove resolved actions from action queue
            actions -= resolvedActions
            // Reduce the stored state with the resolved actions, or set as null if action queue is empty
            storedState = actions.emptyAsNull()?.fold(storedState) { state, action ->
                state?.let { reducer.reduce(state, action) }
            }
            // Optimistic state is still valid, return it.
            return state
        }

        fun reduceFailure(state: S, action: Action): S {
            // Remove the failed action
            actions = actions.filter { it is Action && it.id() != action.id() }
            // Collect all actions until an unresolved optimistic action is found
            val resolvedActions = actions.takeWhile { it !is Action || it.status() == Status.RESOLVED }
            // Remove resolved actions from action queue
            actions -= resolvedActions
            // Reduce the stored state with the resolved actions
            val newState = actions.fold(storedState!!) { state, action -> reducer.reduce(state, action) }
            // Clear the stored state if the action queue is empty
            if (actions.isEmpty()) storedState = null
            // Return newly calculated state
            return newState
        }

        if (action is Action) {
            when (action.status()) {
                Status.PENDING -> reducePending(state, action)
                Status.SUCCESS -> reduceSuccess(state, action)
                Status.FAILURE -> reduceFailure(state, action)
                Status.RESOLVED -> reducer.reduce(state, action)
            }
        }
        else {
            reducer.reduce(state, action)
        }
    }
}
