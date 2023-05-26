/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.stack;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.when;

import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.state.StateTestBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavepointStackImplTest extends StateTestBase {

    private static final Configuration BASE_CONFIGURATION = new HederaTestConfigBuilder(false).getOrCreateConfig();
    private static final String FOOD_SERVICE = "FOOD_SERVICE";

    private static final Map<String, String> BASE_DATA = Map.of(
            A_KEY, APPLE,
            B_KEY, BANANA,
            C_KEY, CHERRY,
            D_KEY, DATE,
            E_KEY, EGGPLANT,
            F_KEY, FIG,
            G_KEY, GRAPE);

    @Mock(strictness = LENIENT)
    private HederaState baseState;

    @BeforeEach
    void setup() {
        final var baseKVState = new MapWritableKVState<>(FRUIT_STATE_KEY, BASE_DATA);
        final var writableStates =
                MapWritableStates.builder().state(baseKVState).build();
        when(baseState.createReadableStates(FOOD_SERVICE)).thenReturn(writableStates);
        when(baseState.createWritableStates(FOOD_SERVICE)).thenReturn(writableStates);
    }

    @Test
    void testConstructor() {
        // when
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);

        // then
        assertThat(stack.depth()).isEqualTo(1);
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().configuration()).isEqualTo(BASE_CONFIGURATION);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidParameters() {
        assertThatThrownBy(() -> new SavepointStackImpl(null, BASE_CONFIGURATION))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SavepointStackImpl(baseState, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void testInitialCreatedSavepoint() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);

        // when
        stack.createSavepoint();

        // then
        assertThat(stack.depth()).isEqualTo(2);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(BASE_DATA));
        assertThat(writableStatesStack).has(content(BASE_DATA));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().configuration()).isEqualTo(BASE_CONFIGURATION);
    }

    @Test
    void testModifiedSavepoint() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig = new HederaTestConfigBuilder(false).getOrCreateConfig();

        // when
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig);

        // then
        assertThat(stack.depth()).isEqualTo(2);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().configuration()).isEqualTo(newConfig);
    }

    @Test
    void testMultipleSavepoints() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig1 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        final var newConfig2 = new HederaTestConfigBuilder(false).getOrCreateConfig();

        // when
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig1);
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(D_KEY, DRAGONFRUIT);
        stack.configuration(newConfig2);

        // then
        assertThat(stack.depth()).isEqualTo(3);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().configuration()).isEqualTo(newConfig2);
    }

    @Test
    void testRolledBackSavepoint() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig = new HederaTestConfigBuilder(false).getOrCreateConfig();
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig);

        // when
        stack.rollback();

        // then
        assertThat(stack.depth()).isEqualTo(1);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(BASE_DATA));
        assertThat(writableStatesStack).has(content(BASE_DATA));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.peek().configuration()).isEqualTo(BASE_CONFIGURATION);
    }

    @Test
    void testModificationsAfterRollback() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig1 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        final var newConfig2 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig1);
        stack.rollback();

        // when
        writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(D_KEY, DRAGONFRUIT);
        stack.configuration(newConfig2);

        // then
        assertThat(stack.depth()).isEqualTo(1);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().configuration()).isEqualTo(newConfig2);
    }

    @Test
    void testNewSavepointAfterRollback() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig1 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        final var newConfig2 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig1);
        stack.rollback();

        // when
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(D_KEY, DRAGONFRUIT);
        stack.configuration(newConfig2);

        // then
        assertThat(stack.depth()).isEqualTo(2);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().configuration()).isEqualTo(newConfig2);
    }

    @Test
    void testMultipleRollbacks() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var readableStatesStack = stack.createReadableStates(FOOD_SERVICE);
        final var writableStatesStack = stack.createWritableStates(FOOD_SERVICE);
        final var newConfig1 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        final var newConfig2 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        final var newConfig3 = new HederaTestConfigBuilder(false).getOrCreateConfig();
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        stack.configuration(newConfig1);
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(C_KEY, CRANBERRY);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(D_KEY, DRAGONFRUIT);
        stack.configuration(newConfig2);
        stack.createSavepoint();
        writableStatesStack.get(FRUIT_STATE_KEY).put(E_KEY, ELDERBERRY);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(F_KEY, FEIJOA);
        stack.configuration(newConfig3);

        // when
        stack.rollback(2);

        // then
        assertThat(stack.depth()).isEqualTo(2);
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(newData));
        assertThat(writableStatesStack).has(content(newData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(stack.peek().configuration()).isEqualTo(newConfig1);
    }

    @Test
    void testRollbackInitialStackFails() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);

        // then
        assertThatThrownBy(stack::rollback).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testTooManyRollbacksFail() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        stack.createSavepoint();
        stack.createSavepoint();

        // then
        assertThatThrownBy(() -> stack.rollback(3)).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> stack.rollback(2)).doesNotThrowAnyException();
    }

    @Test
    void testCommit() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var writableState = stack.createWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
        writableState.put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));

        // when
        stack.commit();

        // then
        final var newData = new HashMap<>(BASE_DATA);
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(newData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(newData));
    }

    @Test
    void testCommitAfterRollback() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        stack.createSavepoint();
        final var writableState = stack.createWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
        writableState.put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));

        // when
        stack.rollback();
        stack.commit();

        // then
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(BASE_DATA));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(BASE_DATA));
    }

    @Test
    void testStackAfterCommit() {
        // given
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var newConfig = new HederaTestConfigBuilder(false).getOrCreateConfig();

        // when
        stack.commit();

        // then
        assertThatThrownBy(stack::createSavepoint).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stack.rollback(2)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(stack::rollback).isInstanceOf(IllegalStateException.class);
        assertThat(stack.depth()).isZero();
        assertThatThrownBy(stack::peek).isInstanceOf(IllegalStateException.class);
        assertThatCode(stack::commit).doesNotThrowAnyException();
        assertThatThrownBy(() -> stack.configuration(newConfig)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stack.createReadableStates(FOOD_SERVICE)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> stack.createWritableStates(FOOD_SERVICE)).isInstanceOf(IllegalStateException.class);
    }

    private static Condition<ReadableStates> content(Map<String, String> expected) {
        return new Condition<>(contentCheck(expected), "state " + expected);
    }

    private static Predicate<ReadableStates> contentCheck(Map<String, String> expected) {
        return readableStates -> {
            final var actual = readableStates.get(FRUIT_STATE_KEY);
            if (expected.size() != actual.size()) {
                return false;
            }
            for (final var entry : expected.entrySet()) {
                if (!Objects.equals(entry.getValue(), actual.get(entry.getKey()))) {
                    return false;
                }
            }
            return true;
        };
    }
}
