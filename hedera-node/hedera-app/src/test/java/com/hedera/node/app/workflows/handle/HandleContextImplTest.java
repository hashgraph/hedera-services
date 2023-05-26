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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.spi.fixtures.Scenarios.ERIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.records.RecordListBuilder;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.state.StateTestBase;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.stack.Savepoint;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.junit.jupiter.MockitoExtension;

@SuppressWarnings("JUnitMalformedDeclaration")
@ExtendWith(MockitoExtension.class)
class HandleContextImplTest extends StateTestBase {

    @Mock
    private SingleTransactionRecordBuilder recordBuilder;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private HandleContextVerifier verifier;

    @Mock(strictness = LENIENT)
    private RecordListBuilder recordListBuilder;

    @Mock
    private TransactionChecker checker;

    @Mock(strictness = Strictness.LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock(strictness = Strictness.LENIENT)
    private ServiceScopeLookup serviceScopeLookup;

    @BeforeEach
    void setup() {
        when(serviceScopeLookup.getServiceName(any())).thenReturn(TokenService.NAME);
    }

    private HandleContextImpl createContext(TransactionBody txBody) {
        return new HandleContextImpl(
                txBody,
                TransactionCategory.USER,
                recordBuilder,
                stack,
                verifier,
                recordListBuilder,
                checker,
                dispatcher,
                serviceScopeLookup);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        assertThatThrownBy(() -> new HandleContextImpl(
                        null,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        null,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        null,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        null,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        null,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        null,
                        checker,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        null,
                        dispatcher,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        null,
                        serviceScopeLookup))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new HandleContextImpl(
                        TransactionBody.DEFAULT,
                        TransactionCategory.USER,
                        recordBuilder,
                        stack,
                        verifier,
                        recordListBuilder,
                        checker,
                        dispatcher,
                        null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("Handling of transaction data")
    final class TransactionDataTest {
        @Test
        void testGetBody() {
            // given
            final var txBody = TransactionBody.newBuilder().build();
            final var context = createContext(txBody);

            // when
            final var actual = context.body();

            // then
            assertThat(actual).isEqualTo(txBody);
        }
    }

    @Nested
    @DisplayName("Handling of stack data")
    final class StackDataTest {

        @Mock
        private Savepoint savepoint1;

        @Mock
        private Savepoint savepoint2;

        @Test
        void testGetStack() {
            // given
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.savepointStack();

            // then
            assertThat(actual).isEqualTo(stack);
        }

        @Test
        void testAccessConfig() {
            // given
            final var configuration1 = new HederaTestConfigBuilder().getOrCreateConfig();
            final var configuration2 = new HederaTestConfigBuilder().getOrCreateConfig();
            when(savepoint1.configuration()).thenReturn(configuration1);
            when(savepoint2.configuration()).thenReturn(configuration2);
            when(stack.peek()).thenReturn(savepoint1);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual1 = context.configuration();
            when(stack.peek()).thenReturn(savepoint2);
            final var actual2 = context.configuration();

            // then
            assertThat(actual1).isSameAs(configuration1);
            assertThat(actual2).isSameAs(configuration2);
        }

        @Test
        void testNewEntityNum() {
            // given
            when(savepoint1.newEntityNum()).thenReturn(1L);
            when(savepoint2.newEntityNum()).thenReturn(2L);
            when(stack.peek()).thenReturn(savepoint1);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual1 = context.newEntityNum();
            when(stack.peek()).thenReturn(savepoint2);
            final var actual2 = context.newEntityNum();

            // then
            assertThat(actual1).isSameAs(1L);
            assertThat(actual2).isSameAs(2L);
        }

        @Test
        void testAccessAttributeValidator(
                @Mock AttributeValidator attributeValidator1, @Mock AttributeValidator attributeValidator2) {
            // given
            when(savepoint1.attributeValidator()).thenReturn(attributeValidator1);
            when(savepoint2.attributeValidator()).thenReturn(attributeValidator2);
            when(stack.peek()).thenReturn(savepoint1);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual1 = context.attributeValidator();
            when(stack.peek()).thenReturn(savepoint2);
            final var actual2 = context.attributeValidator();

            // then
            assertThat(actual1).isSameAs(attributeValidator1);
            assertThat(actual2).isSameAs(attributeValidator2);
        }

        @Test
        void testAccessExpiryValidator(@Mock ExpiryValidator expiryValidator1, @Mock ExpiryValidator expiryValidator2) {
            // given
            when(savepoint1.expiryValidator()).thenReturn(expiryValidator1);
            when(savepoint2.expiryValidator()).thenReturn(expiryValidator2);
            when(stack.peek()).thenReturn(savepoint1);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual1 = context.expiryValidator();
            when(stack.peek()).thenReturn(savepoint2);
            final var actual2 = context.expiryValidator();

            // then
            assertThat(actual1).isSameAs(expiryValidator1);
            assertThat(actual2).isSameAs(expiryValidator2);
        }

        @Test
        void testCreateReadableStore(@Mock ReadableStates readableStates) {
            // given
            when(stack.createReadableStates(TokenService.NAME)).thenReturn(readableStates);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var store = context.readableStore(ReadableAccountStore.class);

            // then
            assertThat(store).isNotNull();
        }

        @Test
        void testCreateWritableStore(@Mock WritableStates writableStates) {
            // given
            when(stack.createWritableStates(TokenService.NAME)).thenReturn(writableStates);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var store = context.writableStore(WritableAccountStore.class);

            // then
            assertThat(store).isNotNull();
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testCreateStoreWithInvalidParameters() {
            // given
            final var context = createContext(TransactionBody.DEFAULT);

            // then
            assertThatThrownBy(() -> context.readableStore(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.readableStore(List.class)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> context.writableStore(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.writableStore(List.class)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("Handling of verification data")
    final class VerificationDataTest {
        @SuppressWarnings("ConstantConditions")
        @Test
        void testVerificationForWithInvalidParameters() {
            // given
            final var context = createContext(TransactionBody.DEFAULT);

            // then
            assertThatThrownBy(() -> context.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void testVerificationForKey(@Mock SignatureVerification verification) {
            // given
            when(verifier.verificationFor(Key.DEFAULT)).thenReturn(verification);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.verificationFor(Key.DEFAULT);

            // then
            assertThat(actual).isEqualTo(verification);
        }

        @Test
        void testVerificationForAlias(@Mock SignatureVerification verification) {
            // given
            when(verifier.verificationFor(ERIN.account().alias())).thenReturn(verification);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.verificationFor(ERIN.account().alias());

            // then
            assertThat(actual).isEqualTo(verification);
        }
    }

    @Nested
    @DisplayName("Handling of record builder")
    final class RecordBuilderTest {

        @BeforeEach
        void setup(@Mock Savepoint savepoint) {
            final var configuration = new HederaTestConfigBuilder().getOrCreateConfig();
            lenient().when(savepoint.configuration()).thenReturn(configuration);
            lenient().when(stack.peek()).thenReturn(savepoint);
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testMethodsWithInvalidParameters() {
            // given
            final var context = createContext(TransactionBody.DEFAULT);

            // then
            assertThatThrownBy(() -> context.recordBuilder(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.recordBuilder(List.class)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> context.addChildRecordBuilder(null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.addChildRecordBuilder(List.class))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> context.addRemovableChildRecordBuilder(null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.addRemovableChildRecordBuilder(List.class))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void testGetRecordBuilder() {
            // given
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.recordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(recordBuilder);
        }

        @Test
        void testAddChildRecordBuilder(@Mock SingleTransactionRecordBuilder childRecordBuilder) {
            // given
            when(recordListBuilder.addChild(any())).thenReturn(childRecordBuilder);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.addChildRecordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(childRecordBuilder);
        }

        @Test
        void testAddRemovableChildRecordBuilder(@Mock SingleTransactionRecordBuilder childRecordBuilder) {
            // given
            when(recordListBuilder.addRemovableChild(any())).thenReturn(childRecordBuilder);
            final var context = createContext(TransactionBody.DEFAULT);

            // when
            final var actual = context.addRemovableChildRecordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(childRecordBuilder);
        }
    }

    @Nested
    @DisplayName("Handling of dispatcher")
    final class DispatcherTest {

        private static final String FOOD_SERVICE = "FOOD_SERVICE";
        private static final Map<String, String> BASE_DATA = Map.of(
                A_KEY, APPLE,
                B_KEY, BANANA,
                C_KEY, CHERRY,
                D_KEY, DATE,
                E_KEY, EGGPLANT,
                F_KEY, FIG,
                G_KEY, GRAPE);
        private static final Configuration CONFIG_1 = new TestConfigBuilder().getOrCreateConfig();
        private static final Configuration CONFIG_2 = new TestConfigBuilder().getOrCreateConfig();

        @Mock(strictness = LENIENT)
        private HederaState baseState;

        @Mock(strictness = LENIENT)
        private SingleTransactionRecordBuilder childRecordBuilder;

        private SavepointStackImpl stack;

        @BeforeEach
        void setup() {
            final var baseKVState = new MapWritableKVState<>(FRUIT_STATE_KEY, BASE_DATA);
            final var writableStates =
                    MapWritableStates.builder().state(baseKVState).build();
            when(baseState.createReadableStates(FOOD_SERVICE)).thenReturn(writableStates);
            when(baseState.createWritableStates(FOOD_SERVICE)).thenReturn(writableStates);

            doAnswer(invocation -> {
                        final var childContext = invocation.getArgument(0, HandleContext.class);
                        final var childStack = (SavepointStackImpl) childContext.savepointStack();
                        childStack
                                .peek()
                                .state()
                                .createWritableStates(FOOD_SERVICE)
                                .get(FRUIT_STATE_KEY)
                                .put(A_KEY, ACAI);
                        childStack.configuration(CONFIG_2);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchHandle(any());

            when(childRecordBuilder.status()).thenReturn(ResponseCodeEnum.OK);
            when(recordListBuilder.addPreceding(any())).thenReturn(childRecordBuilder);
            when(recordListBuilder.addChild(any())).thenReturn(childRecordBuilder);
            when(recordListBuilder.addRemovableChild(any())).thenReturn(childRecordBuilder);

            stack = new SavepointStackImpl(baseState, CONFIG_1);
        }

        private HandleContextImpl createContext(TransactionBody txBody, TransactionCategory category) {
            return new HandleContextImpl(
                    txBody,
                    category,
                    recordBuilder,
                    stack,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup);
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testDispatchWithInvalidArguments() {
            // given
            final var context = createContext(TransactionBody.DEFAULT, TransactionCategory.USER);

            // then
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(null, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(TransactionBody.DEFAULT, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchChildTransaction(null, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchChildTransaction(TransactionBody.DEFAULT, null))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(
                            () -> context.dispatchRemovableChildTransaction(null, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(TransactionBody.DEFAULT, null))
                    .isInstanceOf(NullPointerException.class);
        }

        private static Stream<Arguments> createContextDispatchers() {
            return Stream.of(
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchPrecedingTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class)),
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchChildTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class)),
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchRemovableChildTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class)));
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchSucceeds(Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            final var txBody = TransactionBody.newBuilder().build();
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(checker).checkTransactionBody(txBody);
            verify(dispatcher).dispatchPureChecks(txBody);
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(ACAI);
            assertThat(context.configuration()).isEqualTo(CONFIG_2);
            verify(childRecordBuilder, never()).status(any());
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchCheckerFails(Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            final var txBody = TransactionBody.newBuilder().build();
            doThrow(new PreCheckException(ResponseCodeEnum.INSUFFICIENT_TX_FEE))
                    .when(checker)
                    .checkTransactionBody(txBody);
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(childRecordBuilder).status(ResponseCodeEnum.INSUFFICIENT_TX_FEE);
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchPreHandleFails(Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            final var txBody = TransactionBody.newBuilder().build();
            doThrow(new PreCheckException(ResponseCodeEnum.INVALID_TOPIC_ID))
                    .when(dispatcher)
                    .dispatchPureChecks(txBody);
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(childRecordBuilder).status(ResponseCodeEnum.INVALID_TOPIC_ID);
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchHandleFails(Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            final var txBody = TransactionBody.newBuilder().build();
            doThrow(new HandleException(ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT))
                    .when(dispatcher)
                    .dispatchHandle(any());
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(childRecordBuilder).status(ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @EnumSource(TransactionCategory.class)
        void testDispatchPrecedingWithNonUserTxnFails(TransactionCategory category) {
            if (category != TransactionCategory.USER) {
                // given
                final var context = createContext(TransactionBody.DEFAULT, category);

                // then
                assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                                TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class))
                        .isInstanceOf(IllegalArgumentException.class);
                verify(recordListBuilder, never()).addPreceding(any());
                verify(dispatcher, never()).dispatchHandle(any());
                assertThat(stack.createReadableStates(FOOD_SERVICE)
                                .get(FRUIT_STATE_KEY)
                                .get(A_KEY))
                        .isEqualTo(APPLE);
                assertThat(context.configuration()).isEqualTo(CONFIG_1);
            }
        }

        @Test
        void testDispatchPrecedingWithNonEmptyStackFails() {
            // given
            final var context = createContext(TransactionBody.DEFAULT, TransactionCategory.USER);
            stack.createSavepoint();

            // then
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(IllegalStateException.class);
            verify(recordListBuilder, never()).addPreceding(any());
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
        }

        @Test
        void testDispatchPrecedingWithChangedDataFails() {
            // given
            final var context = createContext(TransactionBody.DEFAULT, TransactionCategory.USER);
            stack.peek()
                    .state()
                    .createWritableStates(FOOD_SERVICE)
                    .get(FRUIT_STATE_KEY)
                    .put(B_KEY, BLUEBERRY);

            // then
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(IllegalStateException.class);
            verify(recordListBuilder, never()).addPreceding(any());
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
        }

        @Test
        void testDispatchChildFromPrecedingFails() {
            // given
            final var context = createContext(TransactionBody.DEFAULT, TransactionCategory.PRECEDING);

            // then
            assertThatThrownBy(() -> context.dispatchChildTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(recordListBuilder, never()).addPreceding(any());
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
        }

        @Test
        void testDispatchRemovableChildFromPrecedingFails() {
            // given
            final var context = createContext(TransactionBody.DEFAULT, TransactionCategory.PRECEDING);

            // then
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(
                            TransactionBody.DEFAULT, SingleTransactionRecordBuilder.class))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(recordListBuilder, never()).addPreceding(any());
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.createReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            assertThat(context.configuration()).isEqualTo(CONFIG_1);
        }
    }
}
