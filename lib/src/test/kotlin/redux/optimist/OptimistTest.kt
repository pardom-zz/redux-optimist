package redux.optimist

import org.jetbrains.spek.api.Spek
import redux.Middleware
import redux.Reducer
import redux.Store
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

class OptimistTest : Spek({

	describe("Optimist") {

		describe("dispatch") {
			val scheduler = TestScheduler()
			val reducer = object : Reducer<State> {
				override fun reduce(state: State, action: Any): State {
					return when (action) {
						is Increment -> state.copy(state.count + 1)
						is Decrement -> state.copy(state.count - 1)
						else -> state
					}
				}
			}

			it("successful transaction preserves optimistic state") {
				val epic = object : Epic<State> {
					override fun map(actions: Observable<out Any>, store: Store<State>): Observable<out Any> {
						return Observable.merge(
								actions.ofType(Increment::class.java)
										.delay(1, TimeUnit.SECONDS, scheduler)
										.map { it.success() },
								actions.ofType(Decrement::class.java)
										.delay(1, TimeUnit.SECONDS, scheduler)
										.map { it.failure() }
						)
					}
				}

				val store = Store.create(
						Optimist.create(reducer),
						State(),
						Middleware.apply(EpicMiddleware.create(epic)))

				store.dispatch(Increment())
				val oldState = store.getState()
				scheduler.advanceTimeBy(2, SECONDS)
				val newState = store.getState()
				assertEquals(oldState, newState)
			}

			it("unsuccessful transaction reverts optimistic state") {
				val epic = object : Epic<State> {
					override fun map(actions: Observable<out Any>, store: Store<State>): Observable<out Any> {
						return Observable.merge(
								actions.ofType(Increment::class.java)
										.delay(1, TimeUnit.SECONDS, scheduler)
										.map { it.success() },
								actions.ofType(Decrement::class.java)
										.delay(1, TimeUnit.SECONDS, scheduler)
										.map { it.failure() }
						)
					}
				}

				val store = Store.create(
						Optimist.create(reducer),
						State(),
						Middleware.apply(EpicMiddleware.create(epic)))

				val oldState = store.getState()
				store.dispatch(Decrement())
				scheduler.advanceTimeBy(2, SECONDS)
				val newState = store.getState()
				assertEquals(oldState, newState)
			}

		}

	}

})
