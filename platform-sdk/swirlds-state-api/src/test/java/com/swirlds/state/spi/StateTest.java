/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.state.spi;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;

import com.swirlds.state.State;
import com.swirlds.state.StateChangeListener;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class StateTest {
    @Test
    void defaultCommitListenerIsNotSupported() {
        final var subject = mock(State.class);
        final var listener = mock(StateChangeListener.class);

        doCallRealMethod().when(subject).registerCommitListener(listener);
        assertThatThrownBy(() -> subject.registerCommitListener(listener))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void changeListenerHasDefaultNoopImplementations() {
        final var subject = new StateChangeListener() {
            @Override
            public Set<StateType> stateTypes() {
                return EnumSet.allOf(StateType.class);
            }

            @Override
            public int stateIdFor(@NonNull final String serviceName, @NonNull final String stateKey) {
                return 0;
            }
        };

        assertThatCode(() -> subject.mapUpdateChange(0, "key", "value")).doesNotThrowAnyException();
        assertThatCode(() -> subject.mapDeleteChange(0, "key")).doesNotThrowAnyException();
        assertThatCode(() -> subject.singletonUpdateChange(0, "value")).doesNotThrowAnyException();
        assertThatCode(() -> subject.queuePushChange(0, "value")).doesNotThrowAnyException();
        assertThatCode(() -> subject.queuePopChange(0)).doesNotThrowAnyException();
    }
}
