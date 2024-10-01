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

package com.hedera.node.app.workflows.handle.dispatch;

import static com.hedera.hapi.node.base.HederaFunctionality.CONTRACT_CALL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.util.UnknownHederaFunctionality;
import com.hedera.node.app.blocks.RecordTranslator;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.services.ServiceScopeLookup;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.throttle.ThrottleAdviser;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.store.ReadableStoreFactory;
import com.hedera.node.app.workflows.dispatcher.TransactionDispatcher;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.DispatchProcessor;
import com.hedera.node.app.workflows.handle.record.RecordStreamBuilder;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.info.NetworkInfo;
import com.swirlds.state.spi.info.NodeInfo;
import java.time.Instant;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChildDispatchFactoryTest {
    public static final Key AN_ED25519_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    public static final Key A_CONTRACT_ID_KEY =
            Key.newBuilder().contractID(ContractID.DEFAULT).build();
    public static final Key A_THRESHOLD_KEY = Key.newBuilder()
            .thresholdKey(ThresholdKey.newBuilder()
                    .threshold(1)
                    .keys(KeyList.newBuilder().keys(AN_ED25519_KEY, A_CONTRACT_ID_KEY)))
            .build();

    @Mock(strictness = LENIENT)
    private TransactionDispatcher dispatcher;

    @Mock(strictness = LENIENT)
    private VerificationAssistant assistant;

    @Mock(strictness = LENIENT)
    private HandleContext handleContext;

    @Mock(strictness = LENIENT)
    private Dispatch parentDispatch;

    @Mock(strictness = LENIENT)
    private Predicate<Key> verifierCallback;

    @Mock(strictness = LENIENT)
    private ReadableStoreFactory readableStoreFactory;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock(strictness = LENIENT)
    private NodeInfo creatorInfo;

    @Mock(strictness = LENIENT)
    private SavepointStackImpl savepointStack;

    @Mock
    private ThrottleAdviser throttleAdviser;

    @Mock
    private Authorizer authorizer;

    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private FeeManager feeManager;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    @Mock
    private DispatchProcessor dispatchProcessor;

    @Mock
    private ServiceScopeLookup serviceScopeLookup;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock
    private RecordTranslator recordTranslator;

    private ChildDispatchFactory subject;

    private static final AccountID payerId =
            AccountID.newBuilder().accountNum(1_234L).build();
    private static final CryptoTransferTransactionBody transferBody = CryptoTransferTransactionBody.newBuilder()
            .tokenTransfers(TokenTransferList.newBuilder()
                    .token(TokenID.DEFAULT)
                    .nftTransfers(NftTransfer.newBuilder()
                            .receiverAccountID(AccountID.DEFAULT)
                            .senderAccountID(AccountID.DEFAULT)
                            .serialNumber(1)
                            .build())
                    .build())
            .build();
    private final Configuration configuration = HederaTestConfigBuilder.createConfig();

    private final Predicate<Key> callback = key -> true;
    private final ExternalizedRecordCustomizer customizer = recordBuilder -> recordBuilder;
    private final RecordStreamBuilder.ReversingBehavior reversingBehavior = StreamBuilder.ReversingBehavior.REMOVABLE;

    @BeforeEach
    public void setUp() {
        subject = new ChildDispatchFactory(
                dispatcher,
                authorizer,
                networkInfo,
                feeManager,
                dispatchProcessor,
                serviceScopeLookup,
                storeMetricsService,
                exchangeRateManager,
                recordTranslator);
    }

    @Test
    void noOpKeyVerifierAlwaysPasses() {
        final var noOpKeyVerifier = new ChildDispatchFactory.NoOpKeyVerifier();
        assertThat(noOpKeyVerifier.verificationFor(Key.DEFAULT).passed()).isTrue();
        assertThat(noOpKeyVerifier.verificationFor(Key.DEFAULT, assistant).passed())
                .isTrue();
        assertThat(noOpKeyVerifier.verificationFor(Bytes.EMPTY).passed()).isTrue();
        assertThat(noOpKeyVerifier.numSignaturesVerified()).isEqualTo(0L);
    }

    @Test
    void keyVerifierWithNullCallbackIsNoOp() {
        assertThat(ChildDispatchFactory.getKeyVerifier(null)).isInstanceOf(ChildDispatchFactory.NoOpKeyVerifier.class);
    }

    @Test
    void keyVerifierOnlySupportsKeyVerification() {
        final var derivedVerifier = ChildDispatchFactory.getKeyVerifier(verifierCallback);
        assertThatThrownBy(() -> derivedVerifier.verificationFor(Key.DEFAULT, assistant))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> derivedVerifier.verificationFor(Bytes.EMPTY))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(derivedVerifier.numSignaturesVerified()).isEqualTo(0L);
    }

    @Test
    void keyVerifierPassesImmediatelyGivenTrueCallback() {
        final var derivedVerifier = ChildDispatchFactory.getKeyVerifier(verifierCallback);
        given(verifierCallback.test(AN_ED25519_KEY)).willReturn(true);
        assertThat(derivedVerifier.verificationFor(AN_ED25519_KEY).passed()).isTrue();
    }

    @Test
    void keyVerifierUsesDelegateIfNotImmediatePass() {
        final var derivedVerifier = ChildDispatchFactory.getKeyVerifier(verifierCallback);
        given(verifierCallback.test(A_THRESHOLD_KEY)).willReturn(false);
        given(verifierCallback.test(AN_ED25519_KEY)).willReturn(true);
        assertThat(derivedVerifier.verificationFor(A_THRESHOLD_KEY).passed()).isTrue();
    }

    @Test
    void keyVerifierDetectsNoPass() {
        final var derivedVerifier = ChildDispatchFactory.getKeyVerifier(verifierCallback);
        assertThat(derivedVerifier.verificationFor(A_THRESHOLD_KEY).passed()).isFalse();
        verify(verifierCallback).test(A_THRESHOLD_KEY);
    }

    @Test
    void testFunctionOfTxnThrowsException() {
        mainSetup();
        var txBody = TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().build())
                .memo("Test Memo")
                .build();
        Exception exception = assertThrows(
                IllegalArgumentException.class,
                () -> subject.createChildDispatch(
                        txBody,
                        callback,
                        payerId,
                        HandleContext.TransactionCategory.SCHEDULED,
                        customizer,
                        reversingBehavior,
                        configuration,
                        savepointStack,
                        readableStoreFactory,
                        creatorInfo,
                        CONTRACT_CALL,
                        throttleAdviser,
                        Instant.ofEpochSecond(12345L),
                        blockRecordInfo,
                        HandleContext.ConsensusThrottling.ON));
        assertTrue(exception.getCause() instanceof UnknownHederaFunctionality);
        assertEquals("Unknown Hedera Functionality", exception.getMessage());
    }

    private void mainSetup() {
        given(parentDispatch.handleContext()).willReturn(handleContext);
        given(handleContext.configuration()).willReturn(configuration);
        given(parentDispatch.readableStoreFactory()).willReturn(readableStoreFactory);
        given(readableStoreFactory.getStore(ReadableAccountStore.class)).willReturn(accountStore);
        given(accountStore.getAccountById(payerId))
                .willReturn(Account.newBuilder().key(Key.DEFAULT).build());
        given(parentDispatch.stack()).willReturn(savepointStack);
    }
}
