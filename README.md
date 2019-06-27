>   Modification of the Tinder StateMachine: https://github.com/Tinder/StateMachine

# StateMachine

[![CircleCI](https://circleci.com/gh/Tinder/StateMachine.svg?style=svg)](https://circleci.com/gh/Tinder/StateMachine)

A Kotlin DSL for finite state machine.

`StateMachine` is used in [Scarlet](https://github.com/Tinder/Scarlet)

### Usage

In this example, we create a `StateMachine` from the following state diagram.

![State Diagram](./example/activity-diagram.png)

Define states, event, and side effects:
~~~kotlin
sealed class State {
    object Solid : State()
    object Liquid : State()
    object Gas : State()
}

sealed class Event {
    object OnMelted : Event()
    object OnFroze : Event()
    object OnVaporized : Event()
    object OnCondensed : Event()
}

~~~

Declare state transitions:
~~~kotlin
val stateMachine = StateMachine.create<State, Event> {
    initialState(State.Solid)
    finalState(State.Gas)
    
    state<State.Solid> {
        on<Event.OnMelted> {
            transitionTo(State.Liquid)
        }
    }

    state<State.Liquid> {
        on<Event.OnFroze> {
            transitionTo(State.Solid)
        }
        on<Event.OnVaporized> {
            transitionTo(State.Gas)
        }
    }
    state<State.Gas> {
        onEnter { oldState, event ->
            // final state
        }
    }
    
    onTransition {
        val validTransition = it as? StateMachine.Transition.Valid ?: return@onTransition
    }

    onFinish {
        // state machine reached the final state
    }
}
~~~

Perform state transitions:
~~~kotlin
assertThat(stateMachine.state).isEqualTo(Solid)

// When
val transition = stateMachine.transition(OnMelted)

// Then
assertThat(stateMachine.state).isEqualTo(Liquid)
assertThat(transition).isEqualTo(
    StateMachine.Transition.Valid(Solid, OnMelted, Liquid)
)
~~~

### Visualization
Thanks to @nvinayshetty, you can visualize your state machines right in the IDE using the [State Arts](https://github.com/nvinayshetty/StateArts) Intellij [plugin](https://plugins.jetbrains.com/plugin/12193-state-art).

### Download

`StateMachine` is available in Maven Central.

Snapshots of the development version are available in [Sonatype's `snapshots` repository][snap].

##### Maven:
```xml
<dependency>
    <groupId>com.tinder.statemachine</groupId>
    <artifactId>statemachine</artifactId>
    <version>0.2.0</version>
</dependency>
```

##### Gradle:
```groovy
implementation 'it.sephiroth.android.library:statemachine:0.0.2'
```

### License
~~~
Copyright (c) 2018, Match Group, LLC
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Match Group, LLC nor the names of its contributors
      may be used to endorse or promote products derived from this software
      without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL MATCH GROUP, LLC BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
~~~

[latest-jar]: https://tinder.com/
[snap]: https://oss.sonatype.org/content/repositories/snapshots/
