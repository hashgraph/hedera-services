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

import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.StateTestBase;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import java.util.Objects;
import java.util.function.Predicate;
import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SavepointStackImplTest extends StateTestBase {

    private static final Configuration BASE_CONFIGURATION = new HederaTestConfigBuilder(false).getOrCreateConfig();
    private static final String FOOD_SERVICE = "FOOD_SERVICE";

    private FakeHederaState baseState;

    @BeforeEach
    void setup() {
        baseState = new FakeHederaState();
        baseState.addService(FOOD_SERVICE, writableFruitState());
    }

    private static MapWritableKVState<String, String> writableFruitState() {
        return MapWritableKVState.<String, String>builder(FRUIT_STATE_KEY)
                .value(A_KEY, APPLE)
                .value(B_KEY, BANANA)
                .value(C_KEY, CHERRY)
                .value(D_KEY, DATE)
                .value(E_KEY, EGGPLANT)
                .value(F_KEY, FIG)
                .value(G_KEY, GRAPE)
                .build();
    }

    @Test
    void testConstructor() {
        // when
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);

        // then
        assertThat(stack.depth()).isEqualTo(1);
        final var originalData = writableFruitState();
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(originalData));
        assertThat(writableStatesStack).has(content(originalData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        final var newData = writableFruitState();
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        final var newData = writableFruitState();
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.createWritableStates(FOOD_SERVICE)).isSameAs(writableStatesStack);
        assertThat(readableStatesStack).has(content(originalData));
        assertThat(writableStatesStack).has(content(originalData));
        assertThat(stack.peek().state().createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(stack.peek().state().createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        final var newData = writableFruitState();
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        final var newData = writableFruitState();
        newData.put(C_KEY, CRANBERRY);
        newData.put(D_KEY, DRAGONFRUIT);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        final var originalData = writableFruitState();
        final var newData = writableFruitState();
        newData.put(A_KEY, ACAI);
        newData.put(B_KEY, BLUEBERRY);
        assertThat(baseState.createReadableStates(FOOD_SERVICE)).has(content(originalData));
        assertThat(baseState.createWritableStates(FOOD_SERVICE)).has(content(originalData));
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
        baseState = new FakeHederaState();
        final var fruitBasket = writableFruitState();
        baseState.addService(FOOD_SERVICE, fruitBasket);
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        final var writableState = stack.createWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
        writableState.put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        assertThat(fruitBasket.get(A_KEY)).isEqualTo(APPLE);
        assertThat(fruitBasket.get(B_KEY)).isEqualTo(BANANA);

        // when
        stack.commit();

        // then
        assertThat(fruitBasket.get(A_KEY)).isEqualTo(ACAI);
        assertThat(fruitBasket.get(B_KEY)).isEqualTo(BLUEBERRY);
    }

    @Test
    void testCommitAfterRollback() {
        // given
        baseState = new FakeHederaState();
        final var fruitBasket = writableFruitState();
        baseState.addService(FOOD_SERVICE, fruitBasket);
        final var stack = new SavepointStackImpl(baseState, BASE_CONFIGURATION);
        stack.createSavepoint();
        final var writableState = stack.createWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY);
        writableState.put(A_KEY, ACAI);
        stack.peek()
                .state()
                .createWritableStates(FOOD_SERVICE)
                .get(FRUIT_STATE_KEY)
                .put(B_KEY, BLUEBERRY);
        assertThat(fruitBasket.get(A_KEY)).isEqualTo(APPLE);
        assertThat(fruitBasket.get(B_KEY)).isEqualTo(BANANA);

        // when
        stack.rollback();
        stack.commit();

        // then
        assertThat(fruitBasket.get(A_KEY)).isEqualTo(APPLE);
        assertThat(fruitBasket.get(B_KEY)).isEqualTo(BANANA);
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

    private static Condition<ReadableStates> content(WritableKVState<String, String> expected) {
        return new Condition<>(contentCheck(expected), "state " + expected);
    }

    private static Predicate<ReadableStates> contentCheck(WritableKVState<String, String> expected) {
        return readableStates -> {
            final var actual = readableStates.get(expected.getStateKey());
            if (expected.size() != actual.size()) {
                return false;
            }
            for (final var it = expected.keys(); it.hasNext(); ) {
                final var key = it.next();
                if (!Objects.equals(expected.get(key), actual.get(key))) {
                    return false;
                }
            }
            return true;
        };
    }
}
