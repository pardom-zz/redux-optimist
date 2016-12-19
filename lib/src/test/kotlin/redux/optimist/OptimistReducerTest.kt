package redux.optimist

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.platform.runner.JUnitPlatform
import org.junit.runner.RunWith
import redux.api.Reducer
import redux.api.Store
import redux.applyMiddleware
import redux.createStore
import redux.observable.Epic
import redux.observable.EpicMiddleware
import redux.optimist.Counter.Actions.Decrement
import redux.optimist.Counter.Actions.Increment
import redux.optimist.Counter.State
import rx.Observable
import rx.schedulers.TestScheduler
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals

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

@RunWith(JUnitPlatform::class)
class OptimistReducerTest : Spek({

    describe("OptimistReducer") {

        describe("dispatch") {
            val scheduler = TestScheduler()
            val reducer = Reducer { state: State, action: Any ->
                when (action) {
                    is Increment -> state.copy(state.count + 1)
                    is Decrement -> state.copy(state.count - 1)
                    else -> state
                }
            }

            it("successful transaction preserves optimistic state") {
                val epic = Epic { actions: Observable<out Any>, store: Store<State> ->
                    Observable.merge(
                            actions.ofType(Increment::class.java)
                                    .delay(1, SECONDS, scheduler)
                                    .map { it.success() },
                            actions.ofType(Decrement::class.java)
                                    .delay(1, SECONDS, scheduler)
                                    .map { it.failure() }
                    )
                }

                val store = createStore(
                        createOptimistReducer(reducer),
                        State(),
                        applyMiddleware(EpicMiddleware.create(epic)))

                store.dispatch(Increment())
                val oldState = store.state
                scheduler.advanceTimeBy(2, SECONDS)
                val newState = store.state
                assertEquals(oldState, newState)
            }

            it("unsuccessful transaction reverts optimistic state") {
                val epic = Epic { actions: Observable<out Any>, store: Store<State> ->
                    Observable.merge(
                            actions.ofType(Increment::class.java)
                                    .delay(1, TimeUnit.SECONDS, scheduler)
                                    .map { it.success() },
                            actions.ofType(Decrement::class.java)
                                    .delay(1, TimeUnit.SECONDS, scheduler)
                                    .map { it.failure() }
                    )
                }

                val store = createStore(
                        createOptimistReducer(reducer),
                        State(),
                        applyMiddleware(EpicMiddleware.create(epic)))

                val oldState = store.state
                store.dispatch(Decrement())
                scheduler.advanceTimeBy(2, SECONDS)
                val newState = store.state
                assertEquals(oldState, newState)
            }

        }

    }

})
