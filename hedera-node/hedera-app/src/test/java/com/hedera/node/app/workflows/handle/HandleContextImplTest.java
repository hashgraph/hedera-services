/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.UNRESOLVABLE_REQUIRED_SIGNERS;
import static com.hedera.node.app.spi.HapiUtils.functionOf;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer.NOOP_EXTERNALIZED_RECORD_CUSTOMIZER;
import static com.hedera.node.app.workflows.handle.HandleContextImpl.PrecedingTransactionCategory.LIMITED_CHILD_RECORDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.ChildFeeContextImpl;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.records.ChildRecordFinalizer;
import com.hedera.node.app.service.token.records.CryptoCreateRecordBuilder;
import com.hedera.node.app.service.token.records.ParentRecordFinalizer;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.fixtures.state.MapWritableKVState;
import com.hedera.node.app.spi.fixtures.state.MapWritableStates;
import com.hedera.node.app.spi.fixtures.state.StateTestBase;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.info.SelfNodeInfo;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.HederaRecordCache;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.throttle.NetworkUtilizationManager;
import com.hedera.node.app.throttle.SynchronizedThrottleAccumulator;
import com.hedera.node.app.workflows.SolvencyPreCheck;
import com.hedera.node.app.workflows.TransactionChecker;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.hedera.node.app.workflows.handle.record.SingleTransactionRecordBuilderImpl;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.state.PlatformState;
import java.lang.reflect.InvocationTargetException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
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
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mock.Strictness;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleContextImplTest extends StateTestBase implements Scenarios {

    private static final Configuration DEFAULT_CONFIGURATION = HederaTestConfigBuilder.createConfig();

    private static final Instant DEFAULT_CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private SingleTransactionRecordBuilderImpl recordBuilder;

    @Mock(strictness = LENIENT)
    private SavepointStackImpl stack;

    @Mock
    private KeyVerifier verifier;

    @Mock(strictness = LENIENT)
    private RecordListBuilder recordListBuilder;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private TransactionChecker checker;

    @Mock(strictness = Strictness.LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock(strictness = Strictness.LENIENT)
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    @Mock
    private HederaRecordCache recordCache;

    @Mock
    private FeeManager feeManager;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private Authorizer authorizer;

    @Mock
    private SignatureVerification verification;

    @Mock
    private SolvencyPreCheck solvencyPreCheck;

    @Mock
    private ChildRecordFinalizer childRecordFinalizer;

    @Mock
    private ParentRecordFinalizer parentRecordFinalizer;

    @Mock
    private SynchronizedThrottleAccumulator synchronizedThrottleAccumulator;

    @Mock
    private NetworkUtilizationManager networkUtilizationManager;

    @Mock
    private SelfNodeInfo selfNodeInfo;

    @Mock
    private PlatformState platformState;

    @BeforeEach
    void setup() {
        when(serviceScopeLookup.getServiceName(any())).thenReturn(TokenService.NAME);
    }

    private static TransactionBody defaultTransactionBody() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(ALICE.accountID()))
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
    }

    private static TransactionBody transactionBodyWithoutId() {
        return TransactionBody.newBuilder()
                .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                .build();
    }

    private HandleContextImpl createContext(final TransactionBody txBody) {
        final HederaFunctionality function;
        try {
            function = functionOf(txBody);
        } catch (final UnknownHederaFunctionality e) {
            throw new RuntimeException(e);
        }

        return new HandleContextImpl(
                txBody,
                function,
                0,
                ALICE.accountID(),
                ALICE.account().keyOrThrow(),
                networkInfo,
                TransactionCategory.USER,
                recordBuilder,
                stack,
                DEFAULT_CONFIGURATION,
                verifier,
                recordListBuilder,
                checker,
                dispatcher,
                serviceScopeLookup,
                blockRecordInfo,
                recordCache,
                feeManager,
                exchangeRateManager,
                DEFAULT_CONSENSUS_NOW,
                authorizer,
                solvencyPreCheck,
                childRecordFinalizer,
                parentRecordFinalizer,
                networkUtilizationManager,
                synchronizedThrottleAccumulator,
                platformState);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithInvalidArguments() {
        final var allArgs = new Object[] {
            defaultTransactionBody(),
            HederaFunctionality.CRYPTO_TRANSFER,
            42,
            ALICE.accountID(),
            ALICE.account().keyOrThrow(),
            networkInfo,
            TransactionCategory.USER,
            recordBuilder,
            stack,
            DEFAULT_CONFIGURATION,
            verifier,
            recordListBuilder,
            checker,
            dispatcher,
            serviceScopeLookup,
            blockRecordInfo,
            recordCache,
            feeManager,
            exchangeRateManager,
            DEFAULT_CONSENSUS_NOW,
            authorizer,
            solvencyPreCheck,
            childRecordFinalizer,
            parentRecordFinalizer,
            networkUtilizationManager,
            synchronizedThrottleAccumulator,
            platformState
        };

        final var constructor = HandleContextImpl.class.getConstructors()[0];
        for (int i = 0; i < allArgs.length; i++) {
            final var index = i;
            // Skip signatureMapSize and payerKey
            if (index == 2 || index == 4) {
                continue;
            }
            assertThatThrownBy(() -> {
                        final var argsWithNull = Arrays.copyOf(allArgs, allArgs.length);
                        argsWithNull[index] = null;
                        constructor.newInstance(argsWithNull);
                    })
                    .isInstanceOf(InvocationTargetException.class)
                    .hasCauseInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    @DisplayName("Handling of record list checkpoint creation")
    final class RevertRecordFromCheckPointTest {

        private HandleContextImpl subject;

        @BeforeEach
        void setUp() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
            subject = createContext(defaultTransactionBody());
        }

        @Test
        void success_createRecordListCheckPoint() {
            // given
            var precedingRecord = createRecordBuilder();
            var childRecord = createRecordBuilder();
            given(recordListBuilder.precedingRecordBuilders()).willReturn(List.of(precedingRecord));
            given(recordListBuilder.childRecordBuilders()).willReturn(List.of(childRecord));

            // when
            final var actual = subject.createRecordListCheckPoint();

            // then
            assertThat(actual).isEqualTo(new RecordListCheckPoint(precedingRecord, childRecord));
        }

        @Test
        void success_createRecordListCheckPoint_MultipleRecords() {
            // given
            var precedingRecord = createRecordBuilder();
            var precedingRecord1 = createRecordBuilder();
            var childRecord = createRecordBuilder();
            var childRecord1 = createRecordBuilder();

            given(recordListBuilder.precedingRecordBuilders()).willReturn(List.of(precedingRecord, precedingRecord1));
            given(recordListBuilder.childRecordBuilders()).willReturn(List.of(childRecord, childRecord1));

            // when
            final var actual = subject.createRecordListCheckPoint();

            // then
            assertThat(actual).isEqualTo(new RecordListCheckPoint(precedingRecord1, childRecord1));
        }

        @Test
        void success_createRecordListCheckPoint_null_values() {
            // when
            final var actual = subject.createRecordListCheckPoint();
            // then
            assertThat(actual).isEqualTo(new RecordListCheckPoint(null, null));
        }

        private static SingleTransactionRecordBuilderImpl createRecordBuilder() {
            return new SingleTransactionRecordBuilderImpl(Instant.EPOCH);
        }
    }

    @Nested
    @DisplayName("Handling new EntityNumber")
    final class EntityIdNumTest {

        @Mock
        private WritableStates writableStates;

        @Mock
        private WritableSingletonState<EntityNumber> entityNumberState;

        private HandleContext handleContext;

        @BeforeEach
        void setUp() {
            final var payer = ALICE.accountID();
            final var payerKey = ALICE.account().keyOrThrow();
            when(writableStates.<EntityNumber>getSingleton(anyString())).thenReturn(entityNumberState);
            when(stack.getWritableStates(EntityIdService.NAME)).thenReturn(writableStates);
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
            handleContext = new HandleContextImpl(
                    defaultTransactionBody(),
                    HederaFunctionality.CRYPTO_TRANSFER,
                    0,
                    payer,
                    payerKey,
                    networkInfo,
                    TransactionCategory.USER,
                    recordBuilder,
                    stack,
                    DEFAULT_CONFIGURATION,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup,
                    blockRecordInfo,
                    recordCache,
                    feeManager,
                    exchangeRateManager,
                    DEFAULT_CONSENSUS_NOW,
                    authorizer,
                    solvencyPreCheck,
                    childRecordFinalizer,
                    parentRecordFinalizer,
                    networkUtilizationManager,
                    synchronizedThrottleAccumulator,
                    platformState);
        }

        @Test
        void testNewEntityNumWithInitialState() {
            // when
            final var actual = handleContext.newEntityNum();

            // then
            assertThat(actual).isEqualTo(1L);
            verify(entityNumberState).get();
            verify(entityNumberState).put(EntityNumber.newBuilder().number(1L).build());
        }

        @Test
        void testPeekingAtNewEntityNumWithInitialState() {
            // when
            final var actual = handleContext.peekAtNewEntityNum();

            // then
            assertThat(actual).isEqualTo(1L);
            verify(entityNumberState).get();
            verify(entityNumberState, never()).put(any());
        }

        @Test
        void testNewEntityNum() {
            // given
            when(entityNumberState.get())
                    .thenReturn(EntityNumber.newBuilder().number(42L).build());

            // when
            final var actual = handleContext.newEntityNum();

            // then
            assertThat(actual).isEqualTo(43L);
            verify(entityNumberState).get();
            verify(entityNumberState).put(EntityNumber.newBuilder().number(43L).build());
        }

        @Test
        void testPeekingAtNewEntityNum() {
            // given
            when(entityNumberState.get())
                    .thenReturn(EntityNumber.newBuilder().number(42L).build());

            // when
            final var actual = handleContext.peekAtNewEntityNum();

            // then
            assertThat(actual).isEqualTo(43L);
            verify(entityNumberState).get();
            verify(entityNumberState, never()).put(any());
        }
    }

    @Nested
    @DisplayName("Getters work as expected")
    final class GettersWork {

        @Mock
        private WritableStates writableStates;

        private HandleContext handleContext;

        @BeforeEach
        void setUp() {
            final var payer = ALICE.accountID();
            final var payerKey = ALICE.account().keyOrThrow();
            when(stack.getWritableStates(EntityIdService.NAME)).thenReturn(writableStates);
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
            handleContext = new HandleContextImpl(
                    defaultTransactionBody(),
                    HederaFunctionality.CRYPTO_TRANSFER,
                    0,
                    payer,
                    payerKey,
                    networkInfo,
                    TransactionCategory.USER,
                    recordBuilder,
                    stack,
                    DEFAULT_CONFIGURATION,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup,
                    blockRecordInfo,
                    recordCache,
                    feeManager,
                    exchangeRateManager,
                    DEFAULT_CONSENSUS_NOW,
                    authorizer,
                    solvencyPreCheck,
                    childRecordFinalizer,
                    parentRecordFinalizer,
                    networkUtilizationManager,
                    synchronizedThrottleAccumulator,
                    platformState);
        }

        @Test
        void getsFreezeTime() {
            given(platformState.getFreezeTime()).willReturn(DEFAULT_CONSENSUS_NOW.plusSeconds(1));
            assertThat(handleContext.freezeTime()).isEqualTo(DEFAULT_CONSENSUS_NOW.plusSeconds(1));
        }
    }

    @Nested
    @DisplayName("Handling of transaction data")
    final class TransactionDataTest {
        @BeforeEach
        void setUp() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
        }

        @Test
        void testGetBody() {
            // given
            final var txBody = defaultTransactionBody();
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

        @BeforeEach
        void setUp() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
        }

        @Test
        void testGetStack() {
            // given
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.savepointStack();

            // then
            assertThat(actual).isEqualTo(stack);
        }

        @Test
        void testCreateReadableStore(@Mock final ReadableStates readableStates) {
            // given
            when(stack.getReadableStates(TokenService.NAME)).thenReturn(readableStates);
            final var context = createContext(defaultTransactionBody());

            // when
            final var store = context.readableStore(ReadableAccountStore.class);

            // then
            assertThat(store).isNotNull();
        }

        @Test
        void testCreateWritableStore(@Mock final WritableStates writableStates) {
            // given
            when(stack.getWritableStates(TokenService.NAME)).thenReturn(writableStates);
            final var context = createContext(defaultTransactionBody());

            // when
            final var store = context.writableStore(WritableAccountStore.class);

            // then
            assertThat(store).isNotNull();
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testCreateStoreWithInvalidParameters() {
            // given
            final var context = createContext(defaultTransactionBody());

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
        @BeforeEach
        void setUp() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testVerificationForWithInvalidParameters() {
            // given
            final var context = createContext(defaultTransactionBody());

            // then
            assertThatThrownBy(() -> context.verificationFor((Key) null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.verificationFor((Bytes) null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        void testVerificationForKey() {
            // given
            when(verifier.verificationFor(Key.DEFAULT)).thenReturn(verification);
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.verificationFor(Key.DEFAULT);

            // then
            assertThat(actual).isEqualTo(verification);
        }

        @Test
        void testVerificationForAlias() {
            // given
            when(verifier.verificationFor(ERIN.account().alias())).thenReturn(verification);
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.verificationFor(ERIN.account().alias());

            // then
            assertThat(actual).isEqualTo(verification);
        }
    }

    @Nested
    @DisplayName("Requesting keys of child transactions")
    final class KeyRequestTest {

        private HandleContext context;

        @BeforeEach
        void setup() {
            when(stack.getReadableStates(TokenService.NAME)).thenReturn(defaultTokenReadableStates());
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());

            context = createContext(defaultTransactionBody());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testAllKeysForTransactionWithInvalidParameters() {
            // given
            final var bob = BOB.accountID();

            // when
            assertThatThrownBy(() -> context.allKeysForTransaction(null, bob)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.allKeysForTransaction(defaultTransactionBody(), null))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        void testAllKeysForTransactionSuccess() throws PreCheckException {
            // given
            doAnswer(invocation -> {
                        final var innerContext = invocation.getArgument(0, PreHandleContext.class);
                        innerContext.requireKey(BOB.account().key());
                        innerContext.optionalKey(CAROL.account().key());
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchPreHandle(any());

            // when
            final var keys = context.allKeysForTransaction(defaultTransactionBody(), ERIN.accountID());
            assertThat(keys.payerKey()).isEqualTo(ERIN.account().key());
            assertThat(keys.requiredNonPayerKeys())
                    .containsExactly(BOB.account().key());
            assertThat(keys.optionalNonPayerKeys())
                    .containsExactly(CAROL.account().key());
        }

        @Test
        void testAllKeysForTransactionWithFailingPureCheck() throws PreCheckException {
            // given
            doThrow(new PreCheckException(INVALID_TRANSACTION_BODY))
                    .when(dispatcher)
                    .dispatchPureChecks(any());

            // when
            assertThatThrownBy(() -> context.allKeysForTransaction(defaultTransactionBody(), ERIN.accountID()))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(INVALID_TRANSACTION_BODY));
        }

        @Test
        void testAllKeysForTransactionWithFailingPreHandle() throws PreCheckException {
            doThrow(new PreCheckException(INSUFFICIENT_ACCOUNT_BALANCE))
                    .when(dispatcher)
                    .dispatchPreHandle(any());

            // gathering keys should not throw exceptions except for inability to read a key.
            assertThatThrownBy(() -> context.allKeysForTransaction(defaultTransactionBody(), ERIN.accountID()))
                    .isInstanceOf(PreCheckException.class)
                    .has(responseCode(UNRESOLVABLE_REQUIRED_SIGNERS));
        }
    }

    @Nested
    @DisplayName("Requesting network info")
    final class NetworkInfoTest {

        private HandleContext context;

        @BeforeEach
        void setup() {
            when(stack.getReadableStates(TokenService.NAME)).thenReturn(defaultTokenReadableStates());
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());

            context = createContext(defaultTransactionBody());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void exposesGivenNetworkInfo() {
            assertSame(networkInfo, context.networkInfo());
        }
    }

    @Nested
    @DisplayName("Dispatching fee computation")
    final class FeeDispatchTest {

        private HandleContext context;

        @BeforeEach
        void setup() {
            when(stack.getReadableStates(TokenService.NAME)).thenReturn(defaultTokenReadableStates());
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());

            context = createContext(defaultTransactionBody());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void invokesComputeFeesDispatchWithChildFeeContextImpl() {
            final var fees = new Fees(1L, 2L, 3L);
            given(dispatcher.dispatchComputeFees(any())).willReturn(fees);
            final var captor = ArgumentCaptor.forClass(FeeContext.class);
            final var result = context.dispatchComputeFees(
                    defaultTransactionBody(), account1002, ComputeDispatchFeesAsTopLevel.NO);
            verify(dispatcher).dispatchComputeFees(captor.capture());
            final var feeContext = captor.getValue();
            assertInstanceOf(ChildFeeContextImpl.class, feeContext);
            assertSame(fees, result);
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void invokesComputeFeesDispatchWithNoTransactionId() {
            given(recordBuilder.consensusNow()).willReturn(DEFAULT_CONSENSUS_NOW);
            final var fees = new Fees(1L, 2L, 3L);
            given(dispatcher.dispatchComputeFees(any())).willReturn(fees);
            final var captor = ArgumentCaptor.forClass(FeeContext.class);
            final var result = context.dispatchComputeFees(
                    transactionBodyWithoutId(), account1002, ComputeDispatchFeesAsTopLevel.NO);
            verify(dispatcher).dispatchComputeFees(captor.capture());
            final var feeContext = captor.getValue();
            assertInstanceOf(ChildFeeContextImpl.class, feeContext);
            assertSame(fees, result);
        }
    }

    @Nested
    @DisplayName("Creating Service APIs")
    final class ServiceApiTest {

        private HandleContext context;

        @BeforeEach
        void setup() {
            when(stack.getReadableStates(TokenService.NAME)).thenReturn(defaultTokenReadableStates());
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());

            context = createContext(defaultTransactionBody());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void failsAsExpectedWithoutAvailableApi() {
            assertThrows(IllegalArgumentException.class, () -> context.serviceApi(Object.class));
        }
    }

    @Nested
    @DisplayName("Handling of record builder")
    final class RecordBuilderTest {

        @BeforeEach
        void setup() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testMethodsWithInvalidParameters() {
            // given
            final var context = createContext(defaultTransactionBody());

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
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.recordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(recordBuilder);
        }

        @Test
        void testAddChildRecordBuilder(@Mock final SingleTransactionRecordBuilderImpl childRecordBuilder) {
            // given
            when(recordListBuilder.addChild(any(), any())).thenReturn(childRecordBuilder);
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.addChildRecordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(childRecordBuilder);
        }

        @Test
        void testAddRemovableChildRecordBuilder(@Mock final SingleTransactionRecordBuilderImpl childRecordBuilder) {
            // given
            when(recordListBuilder.addRemovableChild(any())).thenReturn(childRecordBuilder);
            final var context = createContext(defaultTransactionBody());

            // when
            final var actual = context.addRemovableChildRecordBuilder(CryptoCreateRecordBuilder.class);

            // then
            assertThat(actual).isEqualTo(childRecordBuilder);
        }
    }

    @Nested
    @DisplayName("Handling of dispatcher")
    final class DispatcherTest {

        private static final Predicate<Key> VERIFIER_CALLBACK = key -> true;
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

        @Mock(strictness = LENIENT, answer = Answers.RETURNS_SELF)
        private SingleTransactionRecordBuilderImpl childRecordBuilder;

        private SavepointStackImpl stack;

        @BeforeEach
        void setup() {
            final var baseKVState = new MapWritableKVState<>(FRUIT_STATE_KEY, new HashMap<>(BASE_DATA));
            final var writableStates =
                    MapWritableStates.builder().state(baseKVState).build();
            when(baseState.getReadableStates(FOOD_SERVICE)).thenReturn(writableStates);
            when(baseState.getWritableStates(FOOD_SERVICE)).thenReturn(writableStates);
            final var accountsState = new MapWritableKVState<AccountID, Account>("ACCOUNTS");
            accountsState.put(ALICE.accountID(), ALICE.account());
            when(baseState.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder().state(accountsState).build());

            doAnswer(invocation -> {
                        final var childContext = invocation.getArgument(0, HandleContext.class);
                        final var childStack = (SavepointStackImpl) childContext.savepointStack();
                        childStack
                                .peek()
                                .getWritableStates(FOOD_SERVICE)
                                .get(FRUIT_STATE_KEY)
                                .put(A_KEY, ACAI);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchHandle(any());

            when(childRecordBuilder.status()).thenReturn(ResponseCodeEnum.OK);
            when(recordListBuilder.addPreceding(any(), eq(LIMITED_CHILD_RECORDS)))
                    .thenReturn(childRecordBuilder);
            when(recordListBuilder.addReversiblePreceding(any())).thenReturn(childRecordBuilder);
            when(recordListBuilder.addChild(any(), any())).thenReturn(childRecordBuilder);
            when(recordListBuilder.addRemovableChild(any())).thenReturn(childRecordBuilder);
            when(recordListBuilder.addRemovableChildWithExternalizationCustomizer(any(), any()))
                    .thenReturn(childRecordBuilder);

            stack = new SavepointStackImpl(baseState);
        }

        private HandleContextImpl createContext(final TransactionBody txBody, final TransactionCategory category) {
            final HederaFunctionality function;
            try {
                function = functionOf(txBody);
            } catch (final UnknownHederaFunctionality e) {
                throw new RuntimeException(e);
            }

            return new HandleContextImpl(
                    txBody,
                    function,
                    0,
                    ALICE.accountID(),
                    ALICE.account().keyOrThrow(),
                    networkInfo,
                    category,
                    recordBuilder,
                    stack,
                    DEFAULT_CONFIGURATION,
                    verifier,
                    recordListBuilder,
                    checker,
                    dispatcher,
                    serviceScopeLookup,
                    blockRecordInfo,
                    recordCache,
                    feeManager,
                    exchangeRateManager,
                    DEFAULT_CONSENSUS_NOW,
                    authorizer,
                    solvencyPreCheck,
                    childRecordFinalizer,
                    parentRecordFinalizer,
                    networkUtilizationManager,
                    synchronizedThrottleAccumulator,
                    platformState);
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testDispatchWithInvalidArguments() {
            // given
            final var txBody = defaultTransactionBody();
            final var context = createContext(txBody, TransactionCategory.USER);

            // then
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                            null, SingleTransactionRecordBuilder.class, VERIFIER_CALLBACK, AccountID.DEFAULT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() ->
                            context.dispatchPrecedingTransaction(txBody, null, VERIFIER_CALLBACK, AccountID.DEFAULT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                            txBody, SingleTransactionRecordBuilder.class, null, AccountID.DEFAULT))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchChildTransaction(
                            null, SingleTransactionRecordBuilder.class, VERIFIER_CALLBACK, AccountID.DEFAULT, CHILD))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() ->
                            context.dispatchChildTransaction(txBody, null, VERIFIER_CALLBACK, AccountID.DEFAULT, CHILD))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchChildTransaction(
                            txBody,
                            SingleTransactionRecordBuilder.class,
                            (Predicate<Key>) null,
                            AccountID.DEFAULT,
                            CHILD))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(
                            null,
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            AccountID.DEFAULT,
                            NOOP_EXTERNALIZED_RECORD_CUSTOMIZER))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(
                            txBody, null, VERIFIER_CALLBACK, AccountID.DEFAULT, NOOP_EXTERNALIZED_RECORD_CUSTOMIZER))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(
                            txBody,
                            SingleTransactionRecordBuilder.class,
                            (Predicate<Key>) null,
                            AccountID.DEFAULT,
                            NOOP_EXTERNALIZED_RECORD_CUSTOMIZER))
                    .isInstanceOf(NullPointerException.class);
        }

        private static Stream<Arguments> createContextDispatchers() {
            return Stream.of(
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchPrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID())),
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchReversiblePrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID())),
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchChildTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID(),
                            CHILD)),
                    Arguments.of((Consumer<HandleContext>) context -> context.dispatchRemovableChildTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID(),
                            (ignore) -> Transaction.DEFAULT)));
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchSucceeds(final Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            when(authorizer.isAuthorized(eq(ALICE.accountID()), any())).thenReturn(true);
            when(networkInfo.selfNodeInfo()).thenReturn(selfNodeInfo);
            when(selfNodeInfo.nodeId()).thenReturn(0L);
            Mockito.lenient().when(verifier.verificationFor((Key) any())).thenReturn(verification);
            final var txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ALICE.accountID()))
                    .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                    .build();
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(dispatcher).dispatchPureChecks(txBody);
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(ACAI);
            verify(childRecordBuilder).status(SUCCESS);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchPreHandleFails(final Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            final var txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ALICE.accountID()))
                    .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                    .build();
            doThrow(new PreCheckException(ResponseCodeEnum.INVALID_TOPIC_ID))
                    .when(dispatcher)
                    .dispatchPureChecks(txBody);
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(childRecordBuilder).status(ResponseCodeEnum.INVALID_TOPIC_ID);
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @MethodSource("createContextDispatchers")
        void testDispatchHandleFails(final Consumer<HandleContext> contextDispatcher) throws PreCheckException {
            // given
            when(authorizer.isAuthorized(eq(ALICE.accountID()), any())).thenReturn(true);
            when(networkInfo.selfNodeInfo()).thenReturn(selfNodeInfo);
            when(selfNodeInfo.nodeId()).thenReturn(0L);
            Mockito.lenient().when(verifier.verificationFor((Key) any())).thenReturn(verification);
            final var txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder().accountID(ALICE.accountID()))
                    .consensusSubmitMessage(ConsensusSubmitMessageTransactionBody.DEFAULT)
                    .build();
            doThrow(new HandleException(ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT))
                    .when(dispatcher)
                    .dispatchHandle(any());
            final var context = createContext(txBody, TransactionCategory.USER);

            // when
            contextDispatcher.accept(context);

            // then
            verify(childRecordBuilder).status(ResponseCodeEnum.ACCOUNT_DOES_NOT_OWN_WIPED_NFT);
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
            // TODO: Check that record was added to recordListBuilder
        }

        @ParameterizedTest
        @EnumSource(TransactionCategory.class)
        void testDispatchPrecedingWithNonUserTxnFails(final TransactionCategory category) {
            if (category != TransactionCategory.USER && category != TransactionCategory.CHILD) {
                // given
                final var context = createContext(defaultTransactionBody(), category);

                // then
                assertThatThrownBy(() -> context.dispatchPrecedingTransaction(
                                defaultTransactionBody(),
                                SingleTransactionRecordBuilder.class,
                                VERIFIER_CALLBACK,
                                AccountID.DEFAULT))
                        .isInstanceOf(IllegalArgumentException.class);
                assertThatThrownBy(() -> context.dispatchReversiblePrecedingTransaction(
                                defaultTransactionBody(),
                                SingleTransactionRecordBuilder.class,
                                VERIFIER_CALLBACK,
                                AccountID.DEFAULT))
                        .isInstanceOf(IllegalArgumentException.class);
                verify(recordListBuilder, never()).addPreceding(any(), eq(LIMITED_CHILD_RECORDS));
                verify(dispatcher, never()).dispatchHandle(any());
                assertThat(stack.getReadableStates(FOOD_SERVICE)
                                .get(FRUIT_STATE_KEY)
                                .get(A_KEY))
                        .isEqualTo(APPLE);
            }
        }

        @Test
        void testDispatchPrecedingWithNonEmptyStackDoesntFail() {
            // given
            given(networkInfo.selfNodeInfo()).willReturn(selfNodeInfo);
            given(selfNodeInfo.nodeId()).willReturn(0L);
            final var context = createContext(defaultTransactionBody(), TransactionCategory.USER);
            stack.createSavepoint();

            // then
            assertThatNoException()
                    .isThrownBy(() -> context.dispatchPrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            AccountID.DEFAULT));
            assertThatNoException()
                    .isThrownBy(() -> context.dispatchReversiblePrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            AccountID.DEFAULT));
            verify(recordListBuilder, never()).addRemovablePreceding(any());
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
        }

        @Test
        void testDispatchPrecedingWithChangedDataDoesntFail() throws PreCheckException {
            // given
            final var context = createContext(defaultTransactionBody(), TransactionCategory.USER);
            stack.peek().getWritableStates(FOOD_SERVICE).get(FRUIT_STATE_KEY).put(B_KEY, BLUEBERRY);
            when(networkInfo.selfNodeInfo()).thenReturn(selfNodeInfo);
            when(selfNodeInfo.nodeId()).thenReturn(0L);
            Mockito.lenient().when(verifier.verificationFor((Key) any())).thenReturn(verification);
            when(authorizer.isAuthorized(eq(ALICE.accountID()), any())).thenReturn(true);
            // then
            assertThatNoException()
                    .isThrownBy(() -> context.dispatchPrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID()));
            assertThatNoException()
                    .isThrownBy((() -> context.dispatchPrecedingTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            ALICE.accountID())));
            verify(recordListBuilder, times(2)).addPreceding(any(), eq(LIMITED_CHILD_RECORDS));
            verify(dispatcher, times(2)).dispatchHandle(any());
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(ACAI);
        }

        @Test
        void testDispatchChildFromPrecedingFails() {
            // given
            final var context = createContext(defaultTransactionBody(), TransactionCategory.PRECEDING);

            // then
            assertThatThrownBy(() -> context.dispatchChildTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            AccountID.DEFAULT,
                            CHILD))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(recordListBuilder, never()).addPreceding(any(), eq(LIMITED_CHILD_RECORDS));
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
        }

        @Test
        void testDispatchRemovableChildFromPrecedingFails() {
            // given
            final var context = createContext(defaultTransactionBody(), TransactionCategory.PRECEDING);

            // then
            assertThatThrownBy(() -> context.dispatchRemovableChildTransaction(
                            defaultTransactionBody(),
                            SingleTransactionRecordBuilder.class,
                            VERIFIER_CALLBACK,
                            AccountID.DEFAULT,
                            NOOP_EXTERNALIZED_RECORD_CUSTOMIZER))
                    .isInstanceOf(IllegalArgumentException.class);
            verify(recordListBuilder, never()).addPreceding(any(), eq(LIMITED_CHILD_RECORDS));
            verify(dispatcher, never()).dispatchHandle(any());
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(APPLE);
        }

        @Test
        void testDispatchPrecedingIsCommitted() throws PreCheckException {
            // given
            final var context = createContext(defaultTransactionBody(), TransactionCategory.USER);
            doAnswer(answer -> {
                        stack.getWritableStates(FOOD_SERVICE)
                                .get(FRUIT_STATE_KEY)
                                .put(A_KEY, ACAI);
                        return null;
                    })
                    .when(dispatcher)
                    .dispatchHandle(any());
            given(networkInfo.selfNodeInfo()).willReturn(selfNodeInfo);
            given(selfNodeInfo.nodeId()).willReturn(0L);
            when(authorizer.isAuthorized(eq(ALICE.accountID()), any())).thenReturn(true);
            Mockito.lenient().when(verifier.verificationFor((Key) any())).thenReturn(verification);

            // when
            context.dispatchReversiblePrecedingTransaction(
                    defaultTransactionBody(),
                    SingleTransactionRecordBuilder.class,
                    VERIFIER_CALLBACK,
                    ALICE.accountID());

            // then
            assertThat(stack.depth()).isEqualTo(1);
            assertThat(stack.getReadableStates(FOOD_SERVICE)
                            .get(FRUIT_STATE_KEY)
                            .get(A_KEY))
                    .isEqualTo(ACAI);
            verify(childRecordBuilder).status(SUCCESS);
        }
    }

    @Nested
    @DisplayName("Requesting exchange rate info")
    final class ExchangeRateInfoTest {

        @Mock
        private ExchangeRateInfo exchangeRateInfo;

        private HandleContext context;

        @BeforeEach
        void setup() {
            when(stack.getWritableStates(TokenService.NAME))
                    .thenReturn(MapWritableStates.builder()
                            .state(MapWritableKVState.builder("ACCOUNTS").build())
                            .state(MapWritableKVState.builder("ALIASES").build())
                            .build());
            when(exchangeRateManager.exchangeRateInfo(any())).thenReturn(exchangeRateInfo);

            context = createContext(defaultTransactionBody());
        }

        @SuppressWarnings("ConstantConditions")
        @Test
        void testExchangeRateInfo() {
            assertSame(exchangeRateInfo, context.exchangeRateInfo());
        }
    }
}
