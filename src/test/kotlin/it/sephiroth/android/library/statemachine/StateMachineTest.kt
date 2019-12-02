package it.sephiroth.android.library.statemachine

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.then
import it.sephiroth.android.library.statemachine.StateMachine
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatIllegalArgumentException
import org.assertj.core.api.Assertions.assertThatIllegalStateException
import org.junit.Test
import org.junit.experimental.runners.Enclosed
import org.junit.runner.RunWith

@RunWith(Enclosed::class)
internal class StateMachineTest {

    class MatterStateMachine {

        private var resetCount = 0
        private val logger = mock<Logger>()
        private val stateMachine = StateMachine.create<State, Event> {
            initialState(State.Solid)
            finalStates(arrayOf(State.Gas))
            state<State.Solid> {
                on<Event.OnMelted> {
                    transitionTo(State.Liquid)
                }
            }
            state<State.Liquid> {
                on<Event.OnFrozen> {
                    transitionTo(State.Solid)
                }
                on<Event.OnVaporized> {
                    transitionTo(State.Gas)
                }
            }
            state<State.Gas> {
                on<Event.OnCondensed> {
                    transitionTo(State.Liquid)
                }
            }
            onTransition {
                val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
                when (validTransition.toState) {
                    State.Gas -> logger.log(ON_GAS_MESSAGE)
                    State.Liquid -> logger.log(ON_LIQUID_MESSAGE)
                    State.Solid -> logger.log(ON_SOLID_MESSAGE)
                }
            }

            onFinish {
                logger.log(ON_FINISH_MESSAGE)
            }

            onReset {
                logger.log(ON_RESET_MESSAGE)
                resetCount += 1
            }
        }

        @Test
        fun test() {
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            stateMachine.transition(Event.OnMelted)
            assertThat(stateMachine.state).isEqualTo(State.Liquid)

            stateMachine.reset()
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            assertThat(resetCount).isEqualTo(1)

            stateMachine.reset()
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            assertThat(resetCount).isEqualTo(1)

            stateMachine.transition(Event.OnMelted)
            assertThat(stateMachine.state).isEqualTo(State.Liquid)

            stateMachine.reset()
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            assertThat(resetCount).isEqualTo(2)

            assertThat(stateMachine.inState(State.Solid)).isEqualTo(true)
        }

        @Test
        fun initialState_shouldBeSolid() {
            // Then
            assertThat(stateMachine.state).isEqualTo(State.Solid)
        }

        @Test
        fun givenStateIsSolid_onMelted_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Solid)

            // When
            val transition = stateMachine.transition(Event.OnMelted)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Liquid)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Solid, Event.OnMelted, State.Liquid)
            )
            then(logger).should().log(ON_LIQUID_MESSAGE)
        }

        @Test
        fun givenStateIsLiquid_onFroze_shouldTransitionToSolidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnFrozen)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Solid)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Liquid, Event.OnFrozen, State.Solid)
            )
            then(logger).should().log(ON_SOLID_MESSAGE)
        }

        @Test
        fun givenStateIsLiquid_onVaporized_shouldTransitionToGasStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Liquid)

            // When
            val transition = stateMachine.transition(Event.OnVaporized)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Gas)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(State.Liquid, Event.OnVaporized, State.Gas)
            )
            then(logger).should().log(ON_GAS_MESSAGE)
        }

        @Test
        fun givenStateIsGas_onCondensed_shouldTransitionToLiquidStateAndLog() {
            // Given
            val stateMachine = givenStateIs(State.Gas)

            // When
            val transition = stateMachine.transition(Event.OnCondensed)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Gas)
            assertThat(transition).isEqualTo(null)
        }

        private fun givenStateIs(state: State): StateMachine<State, Event> {
            return stateMachine.with { initialState(state) }
        }

        companion object {
            const val ON_LIQUID_MESSAGE = "I'm liquid"
            const val ON_GAS_MESSAGE = "I'm gas"
            const val ON_SOLID_MESSAGE = "I'm solid"
            const val ON_FINISH_MESSAGE = "I'm finished"
            const val ON_RESET_MESSAGE = "I've been reset"

            sealed class State {
                object Solid : State()
                object Liquid : State()
                object Gas : State()
            }

            sealed class Event {
                object OnMelted : Event()
                object OnFrozen : Event()
                object OnVaporized : Event()
                object OnCondensed : Event()
            }

            interface Logger {
                fun log(message: String)
            }
        }
    }

    class TurnstileStateMachine {

        private val stateMachine = StateMachine.create<State, Event> {
            initialState(State.Locked(credit = 0))
            state<State.Locked> {
                on<Event.InsertCoin> {
                    val newCredit = credit + it.value
                    if (newCredit >= FARE_PRICE) {
                        transitionTo(State.Unlocked)
                    } else {
                        transitionTo(State.Locked(newCredit))
                    }
                }
                on<Event.AdmitPerson> {
                    dontTransition()
                }
                on<Event.MachineDidFail> {
                    transitionTo(State.Broken(this))
                }
            }
            state<State.Unlocked> {
                on<Event.AdmitPerson> {
                    transitionTo(State.Locked(credit = 0))
                }
            }
            state<State.Broken> {
                on<Event.MachineRepairDidComplete> {
                    transitionTo(oldState)
                }
            }
        }

        @Test
        fun initialState_shouldBeLocked() {
            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 0))
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditLessThanFairPrice_shouldTransitionToLockedState() {
            // When
            val transition = stateMachine.transition(Event.InsertCoin(10))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 10))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 0),
                    Event.InsertCoin(10),
                    State.Locked(credit = 10)
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditEqualsFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(15))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Unlocked)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(15),
                    State.Unlocked
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenInsertCoin_andCreditMoreThanFairPrice_shouldTransitionToUnlockedStateAndOpenDoors() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.InsertCoin(20))

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Unlocked)
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.InsertCoin(20),
                    State.Unlocked
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenAdmitPerson_shouldTransitionToLockedStateAndSoundAlarm() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 35))

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 35))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 35),
                    Event.AdmitPerson,
                    State.Locked(credit = 35)
                )
            )
        }

        @Test
        fun givenStateIsLocked_whenMachineDidFail_shouldTransitionToBrokenStateAndOrderRepair() {
            // Given
            val stateMachine = givenStateIs(State.Locked(credit = 15))

            // When
            val transitionToBroken = stateMachine.transition(Event.MachineDidFail)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Broken(oldState = State.Locked(credit = 15)))
            assertThat(transitionToBroken).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Locked(credit = 15),
                    Event.MachineDidFail,
                    State.Broken(oldState = State.Locked(credit = 15))
                )
            )
        }

        @Test
        fun givenStateIsUnlocked_whenAdmitPerson_shouldTransitionToLockedStateAndCloseDoors() {
            // Given
            val stateMachine = givenStateIs(State.Unlocked)

            // When
            val transition = stateMachine.transition(Event.AdmitPerson)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 0))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Unlocked,
                    Event.AdmitPerson,
                    State.Locked(credit = 0)
                )
            )
        }

        @Test
        fun givenStateIsBroken_whenMachineRepairDidComplete_shouldTransitionToLockedState() {
            // Given
            val stateMachine = givenStateIs(State.Broken(oldState = State.Locked(credit = 15)))

            // When
            val transition = stateMachine.transition(Event.MachineRepairDidComplete)

            // Then
            assertThat(stateMachine.state).isEqualTo(State.Locked(credit = 15))
            assertThat(transition).isEqualTo(
                StateMachine.Transition.Valid(
                    State.Broken(oldState = State.Locked(credit = 15)),
                    Event.MachineRepairDidComplete,
                    State.Locked(credit = 15)
                )
            )
        }

        private fun givenStateIs(state: State): StateMachine<State, Event> {
            return stateMachine.with { initialState(state) }
        }

        companion object {
            private const val FARE_PRICE = 50

            sealed class State {
                data class Locked(val credit: Int) : State()
                object Unlocked : State()
                data class Broken(val oldState: State) : State()
            }

            sealed class Event {
                data class InsertCoin(val value: Int) : Event()
                object AdmitPerson : Event()
                object MachineDidFail : Event()
                object MachineRepairDidComplete : Event()
            }
        }
    }

    @RunWith(Enclosed::class)
    class ObjectStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = mock<(StateMachine.Transition<State, Event>) -> Unit>()
            private val onTransitionListener2 = mock<(StateMachine.Transition<State, Event>) -> Unit>()
            private val onStateAExitListener1 = mock<State.(State, Event) -> Unit>()
            private val onStateAExitListener2 = mock<State.(State, Event) -> Unit>()
            private val onStateCEnterListener1 = mock<State.(State, Event) -> Unit>()
            private val onStateCEnterListener2 = mock<State.(State, Event) -> Unit>()
            private val stateMachine = StateMachine.create<State, Event> {
                initialState(State.A)
                state<State.A> {
                    onExit(onStateAExitListener1)
                    onExit(onStateAExitListener2)
                    on<Event.E1> {
                        transitionTo(State.B)
                    }
                    on<Event.E2> {
                        transitionTo(State.C)
                    }
                    on<Event.E4> {
                        transitionTo(State.D)
                    }
                }
                state<State.B> {
                    on<Event.E3> {
                        transitionTo(State.C)
                    }
                }
                state<State.C> {
                    on<Event.E4> {
                        dontTransition()
                    }
                    onEnter(onStateCEnterListener1)
                    onEnter(onStateCEnterListener2)
                }
                onTransition(onTransitionListener1)
                onTransition(onTransitionListener2)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertThat(state).isEqualTo(State.A)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTransition() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(Event.E1)

                // Then
                assertThat(transitionFromStateAToStateB).isEqualTo(
                    StateMachine.Transition.Valid(State.A, Event.E1, State.B)
                )

                // When
                val transitionFromStateBToStateC = stateMachine.transition(Event.E3)

                // Then
                assertThat(transitionFromStateBToStateC).isEqualTo(
                    StateMachine.Transition.Valid(State.B, Event.E3, State.C)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                assertThat(stateMachine.state).isEqualTo(State.B)

                // When
                stateMachine.transition(Event.E3)

                // Then
                assertThat(stateMachine.state).isEqualTo(State.C)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(Event.E1)

                // Then
                then(onTransitionListener1).should().invoke(
                    StateMachine.Transition.Valid(State.A, Event.E1, State.B)
                )

                // When
                stateMachine.transition(Event.E3)

                // Then
                then(onTransitionListener2).should()
                    .invoke(StateMachine.Transition.Valid(State.B, Event.E3, State.C))

                // When
                stateMachine.transition(Event.E4)

                // Then
                then(onTransitionListener2).should()
                    .invoke(StateMachine.Transition.Valid(State.C, Event.E4, State.C))
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                then(onStateCEnterListener1).should().invoke(State.C, State.A, Event.E2)
                then(onStateCEnterListener2).should().invoke(State.C, State.A, Event.E2)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(Event.E2)

                // Then
                then(onStateAExitListener1).should().invoke(State.A, State.C, Event.E2)
                then(onStateAExitListener2).should().invoke(State.A, State.C, Event.E2)
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(Event.E3)

                // Then
                assertThat(transition).isEqualTo(
                    StateMachine.Transition.Invalid<State, Event>(State.A, Event.E3)
                )
                assertThat(stateMachine.state).isEqualTo(fromState)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertThatIllegalStateException()
                    .isThrownBy {
                        stateMachine.transition(Event.E4)
                    }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertThatIllegalArgumentException().isThrownBy {
                    StateMachine.create<State, Event> {}
                }
            }
        }

        private companion object {
            private sealed class State {
                object A : State()
                object B : State()
                object C : State()
                object D : State()
            }

            private sealed class Event {
                object E1 : Event()
                object E2 : Event()
                object E3 : Event()
                object E4 : Event()
            }

            private sealed class SideEffect {
                object SE1 : SideEffect()
                object SE2 : SideEffect()
                object SE3 : SideEffect()
            }
        }
    }

    @RunWith(Enclosed::class)
    class ConstantStateMachine {

        class WithInitialState {

            private val onTransitionListener1 = mock<(StateMachine.Transition<String, Int>) -> Unit>()
            private val onTransitionListener2 = mock<(StateMachine.Transition<String, Int>) -> Unit>()
            private val onStateCEnterListener1 = mock<String.(String, Int) -> Unit>()
            private val onStateCEnterListener2 = mock<String.(String, Int) -> Unit>()
            private val onStateAExitListener1 = mock<String.(String, Int) -> Unit>()
            private val onStateAExitListener2 = mock<String.(String, Int) -> Unit>()
            private val stateMachine = StateMachine.create<String, Int> {
                initialState(STATE_A)
                state(STATE_A) {
                    onExit(onStateAExitListener1)
                    onExit(onStateAExitListener2)
                    on(EVENT_1) {
                        transitionTo(STATE_B)
                    }
                    on(EVENT_2) {
                        transitionTo(STATE_C)
                    }
                    on(EVENT_4) {
                        transitionTo(STATE_D)
                    }
                }
                state(STATE_B) {
                    on(EVENT_3) {
                        transitionTo(STATE_C)
                    }
                }
                state(STATE_C) {
                    onEnter(onStateCEnterListener1)
                    onEnter(onStateCEnterListener2)
                }
                onTransition(onTransitionListener1)
                onTransition(onTransitionListener2)
            }

            @Test
            fun state_shouldReturnInitialState() {
                // When
                val state = stateMachine.state

                // Then
                assertThat(state).isEqualTo(STATE_A)
            }

            @Test
            fun transition_givenValidEvent_shouldReturnTrue() {
                // When
                val transitionFromStateAToStateB = stateMachine.transition(EVENT_1)

                // Then
                assertThat(transitionFromStateAToStateB).isEqualTo(
                    StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B)
                )

                // When
                val transitionFromStateBToStateC = stateMachine.transition(EVENT_3)

                // Then
                assertThat(transitionFromStateBToStateC).isEqualTo(
                    StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldCreateAndSetNewState() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                assertThat(stateMachine.state).isEqualTo(STATE_B)

                // When
                stateMachine.transition(EVENT_3)

                // Then
                assertThat(stateMachine.state).isEqualTo(STATE_C)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnStateChangeListener() {
                // When
                stateMachine.transition(EVENT_1)

                // Then
                then(onTransitionListener1).should().invoke(
                    StateMachine.Transition.Valid(STATE_A, EVENT_1, STATE_B)
                )

                // When
                stateMachine.transition(EVENT_3)

                // Then
                then(onTransitionListener2).should().invoke(
                    StateMachine.Transition.Valid(STATE_B, EVENT_3, STATE_C)
                )
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnEnterListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                then(onStateCEnterListener1).should().invoke(STATE_C, STATE_A, EVENT_2)
                then(onStateCEnterListener2).should().invoke(STATE_C, STATE_A, EVENT_2)
            }

            @Test
            fun transition_givenValidEvent_shouldTriggerOnExitListeners() {
                // When
                stateMachine.transition(EVENT_2)

                // Then
                then(onStateAExitListener1).should().invoke(STATE_A, STATE_C, EVENT_2)
                then(onStateAExitListener2).should().invoke(STATE_A, STATE_C, EVENT_2)
            }

            @Test
            fun transition_givenInvalidEvent_shouldReturnInvalidTransition() {
                // When
                val fromState = stateMachine.state
                val transition = stateMachine.transition(EVENT_3)

                // Then
                assertThat(transition).isEqualTo(
                    StateMachine.Transition.Invalid(STATE_A, EVENT_3)
                )
                assertThat(stateMachine.state).isEqualTo(fromState)
            }

            @Test
            fun transition_givenUndeclaredState_shouldThrowIllegalStateException() {
                // Then
                assertThatIllegalStateException()
                    .isThrownBy {
                        stateMachine.transition(EVENT_4)
                    }
            }
        }

        class WithoutInitialState {

            @Test
            fun create_givenNoInitialState_shouldThrowIllegalArgumentException() {
                // Then
                assertThatIllegalArgumentException().isThrownBy {
                    StateMachine.create<String, Int> {}
                }
            }
        }

        class WithMissingStateDefinition {

            private val stateMachine = StateMachine.create<String, Int> {
                initialState(STATE_A)
                state(STATE_A) {
                    on(EVENT_1) {
                        transitionTo(STATE_B)
                    }
                }
                // Missing STATE_B definition.
            }

            @Test
            fun transition_givenMissingDestinationStateDefinition_shouldThrowIllegalStateExceptionWithStateName() {
                // Then
                assertThatIllegalStateException()
                    .isThrownBy { stateMachine.transition(EVENT_1) }
                    .withMessage("Missing definition for state ${STATE_B.javaClass.simpleName}!")
            }
        }

        private companion object {
            private const val STATE_A = "a"
            private const val STATE_B = "b"
            private const val STATE_C = "c"
            private const val STATE_D = "d"

            private const val EVENT_1 = 1
            private const val EVENT_2 = 2
            private const val EVENT_3 = 3
            private const val EVENT_4 = 4

            private const val SIDE_EFFECT_1 = "alpha"
        }
    }

}
