package com.sephiroth.android.library

import java.util.concurrent.atomic.AtomicReference

class StateMachine<STATE : Any, EVENT : Any> private constructor(
    private val graph: Graph<STATE, EVENT>
) {

    private val stateRef = AtomicReference(graph.initialState)
    private var finished = false

    val state: STATE
        get() = stateRef.get()

    fun reset() {
        synchronized(this) {
            stateRef.set(graph.initialState)
            finished = false
        }
    }

    fun transition(event: EVENT): Transition<STATE, EVENT>? {
        val transition = synchronized(this) {
            if (finished) {
                return null
            }
            val fromState = stateRef.get()
            if (fromState.isFinalState) return null

            val transition = fromState.getTransition(event)

            if (transition is Transition.Valid) {
                if (transition.toState.isFinalState) finished = true
                stateRef.set(transition.toState)
            }
            transition
        }

        if (transition is Transition.Valid) {
            transition.notifyOnTransition()

            with(transition) {
                with(fromState) {
                    notifyOnExit(event, toState)
                }
                with(toState) {
                    notifyOnEnter(event, fromState)
                }
            }

            if (transition.toState.isFinalState) notifyOnFinish()
        }
        return transition
    }

    fun with(init: GraphBuilder<STATE, EVENT>.() -> Unit): StateMachine<STATE, EVENT> {
        return create(graph.copy(initialState = state), init)
    }

    private fun STATE.getTransition(event: EVENT): Transition<STATE, EVENT> {
        for ((eventMatcher, createTransitionTo) in getDefinition().transitions) {
            if (eventMatcher.matches(event)) {
                createTransitionTo(this, event)?.component1()?.let { toState ->
                    return Transition.Valid(this, event, toState)
                }
            }
        }
        return Transition.Invalid(this, event)
    }

    val STATE.isFinalState: Boolean
        get() = this == graph.finalState

    private fun STATE.getDefinition() = graph.stateDefinitions
        .filter { it.key.matches(this) }
        .map { it.value }
        .firstOrNull() ?: error("Missing definition for state ${this.javaClass.simpleName}!")

    private fun STATE.notifyOnEnter(cause: EVENT, fromState: STATE) {
        getDefinition().onEnterListeners.forEach { it(this, fromState, cause) }
    }

    private fun STATE.notifyOnExit(cause: EVENT, toState: STATE) {
        getDefinition().onExitListeners.forEach { it(this, toState, cause) }
    }

    private fun Transition<STATE, EVENT>.notifyOnTransition() {
        graph.onTransitionListeners.forEach { it(this) }
    }

    private fun notifyOnFinish() {
        graph.onFinishListeners.forEach { it(this@StateMachine) }
    }

    @Suppress("UNUSED")
    sealed class Transition<out STATE : Any, out EVENT : Any> {
        abstract val fromState: STATE
        abstract val event: EVENT

        data class Valid<out STATE : Any, out EVENT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT,
            val toState: STATE
        ) : Transition<STATE, EVENT>()

        data class Invalid<out STATE : Any, out EVENT : Any> internal constructor(
            override val fromState: STATE,
            override val event: EVENT
        ) : Transition<STATE, EVENT>()
    }

    data class Graph<STATE : Any, EVENT : Any>(
        val initialState: STATE,
        val finalState: STATE?,
        val stateDefinitions: Map<Matcher<STATE, STATE>, State<STATE, EVENT>>,
        val onTransitionListeners: List<(Transition<STATE, EVENT>) -> Unit>,
        val onFinishListeners: List<(StateMachine<STATE, EVENT>) -> Unit>
    ) {

        class State<STATE : Any, EVENT : Any> internal constructor() {
            val onEnterListeners = mutableListOf<(STATE, STATE, EVENT) -> Unit>()
            val onExitListeners = mutableListOf<(STATE, STATE, EVENT) -> Unit>()
            val transitions =
                linkedMapOf<Matcher<EVENT, EVENT>, (STATE, EVENT) -> TransitionTo<STATE>?>()

            val onFinishListeners = mutableListOf<(StateMachine<STATE, EVENT>) -> Unit>()

            data class TransitionTo<out STATE : Any> internal constructor(
                val toState: STATE
            )
        }
    }

    class Matcher<T : Any, out R : T> private constructor(private val clazz: Class<R>) {

        private val predicates = mutableListOf<(T) -> Boolean>({ clazz.isInstance(it) })

        fun where(predicate: R.() -> Boolean): Matcher<T, R> = apply {
            predicates.add {
                @Suppress("UNCHECKED_CAST")
                (it as R).predicate()
            }
        }

        fun matches(value: T) = predicates.all { it(value) }

        companion object {
            fun <T : Any, R : T> any(clazz: Class<R>): Matcher<T, R> = Matcher(clazz)

            inline fun <T : Any, reified R : T> any(): Matcher<T, R> = any(R::class.java)

            inline fun <T : Any, reified R : T> eq(value: R): Matcher<T, R> =
                any<T, R>().where { this == value }
        }
    }

    class GraphBuilder<STATE : Any, EVENT : Any>(
        graph: Graph<STATE, EVENT>? = null
    ) {
        private var initialState = graph?.initialState
        private var finalState = graph?.finalState
        private val stateDefinitions = LinkedHashMap(graph?.stateDefinitions ?: emptyMap())
        private val onTransitionListeners = ArrayList(graph?.onTransitionListeners ?: emptyList())
        private val onFinishListeners = ArrayList(graph?.onFinishListeners ?: emptyList())

        fun initialState(initialState: STATE) {
            this.initialState = initialState
        }

        fun finalState(finalState: STATE?) {
            this.finalState = finalState
        }

        fun <S : STATE> state(
            stateMatcher: Matcher<STATE, S>,
            init: StateDefinitionBuilder<S>.() -> Unit
        ) {
            stateDefinitions[stateMatcher] = StateDefinitionBuilder<S>().apply(init).build()
        }

        inline fun <reified S : STATE> state(noinline init: StateDefinitionBuilder<S>.() -> Unit) {
            state(Matcher.any(), init)
        }

        inline fun <reified S : STATE> state(
            state: S,
            noinline init: StateDefinitionBuilder<S>.() -> Unit
        ) {
            state(Matcher.eq<STATE, S>(state), init)
        }

        fun onTransition(listener: (Transition<STATE, EVENT>) -> Unit) {
            onTransitionListeners.add(listener)
        }

        fun onFinish(listener: (StateMachine<STATE, EVENT>) -> Unit) {
            onFinishListeners.add(listener)
        }

        fun build(): Graph<STATE, EVENT> {
            return Graph(
                requireNotNull(initialState),
                finalState,
                stateDefinitions.toMap(),
                onTransitionListeners.toList(),
                onFinishListeners.toList()
            )
        }

        inner class StateDefinitionBuilder<S : STATE> {

            private val stateDefinition = Graph.State<STATE, EVENT>()

            inline fun <reified E : EVENT> any(): Matcher<EVENT, E> = Matcher.any()

            inline fun <reified R : EVENT> eq(value: R): Matcher<EVENT, R> = Matcher.eq(value)

            fun <E : EVENT> on(
                eventMatcher: Matcher<EVENT, E>,
                createTransitionTo: S.(E) -> Graph.State.TransitionTo<STATE>?
            ) {
                stateDefinition.transitions[eventMatcher] = { state, event ->
                    @Suppress("UNCHECKED_CAST")
                    createTransitionTo((state as S), event as E)
                }
            }

            inline fun <reified E : EVENT> on(
                noinline createTransitionTo: S.(E) -> Graph.State.TransitionTo<STATE>?
            ) {
                return on(any(), createTransitionTo)
            }

            @Suppress("unused")
            inline fun <reified E : EVENT> on(
                event: E,
                noinline createTransitionTo: S.(E) -> Graph.State.TransitionTo<STATE>?
            ) {
                return on(eq(event), createTransitionTo)
            }

            fun onEnter(listener: S.(STATE, EVENT) -> Unit) = with(stateDefinition) {
                onEnterListeners.add { state, oldState, cause ->
                    @Suppress("UNCHECKED_CAST")
                    listener(state as S, oldState as S, cause)
                }
            }

            fun onExit(listener: S.(STATE, EVENT) -> Unit) = with(stateDefinition) {
                onExitListeners.add { state, newState, cause ->
                    @Suppress("UNCHECKED_CAST")
                    listener(state as S, newState as S, cause)
                }
            }

            fun build() = stateDefinition

            @Suppress("UNUSED") // The unused warning is probably a compiler bug.
            fun S.transitionTo(state: STATE) =
                Graph.State.TransitionTo(state)

            @Suppress("UNUSED") // The unused warning is probably a compiler bug.
            fun S.dontTransition() = transitionTo(this)
        }
    }

    companion object {
        fun <STATE : Any, EVENT : Any> create(
            init: GraphBuilder<STATE, EVENT>.() -> Unit
        ): StateMachine<STATE, EVENT> {
            return create(null, init)
        }

        private fun <STATE : Any, EVENT : Any> create(
            graph: Graph<STATE, EVENT>?,
            init: GraphBuilder<STATE, EVENT>.() -> Unit
        ): StateMachine<STATE, EVENT> {
            return StateMachine(GraphBuilder(graph).apply(init).build())
        }
    }
}