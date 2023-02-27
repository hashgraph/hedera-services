/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.dispatcher;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.service.admin.impl.handlers.FreezeHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusCreateTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusDeleteTopicHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusSubmitMessageHandler;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusUpdateTopicHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCallHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemDeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractSystemUndeleteHandler;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.service.contract.impl.handlers.EtherumTransactionHandler;
import com.hedera.node.app.service.file.impl.handlers.FileAppendHandler;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.handlers.FileDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemDeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileSystemUndeleteHandler;
import com.hedera.node.app.service.file.impl.handlers.FileUpdateHandler;
import com.hedera.node.app.service.network.impl.handlers.NetworkUncheckedSubmitHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.CryptoAddLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoApproveAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteAllowanceHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoDeleteLiveHashHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoTransferHandler;
import com.hedera.node.app.service.token.impl.handlers.CryptoUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAccountWipeHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenAssociateToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenBurnHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenCreateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDeleteHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenDissociateFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFeeScheduleUpdateHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenFreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenGrantKycToAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenPauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenRevokeKycFromAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUnpauseHandler;
import com.hedera.node.app.service.token.impl.handlers.TokenUpdateHandler;
import com.hedera.node.app.service.util.impl.handlers.UtilPrngHandler;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.state.HederaState;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ConsensusCreateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusDeleteTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusSubmitMessageTransactionBody;
import com.hederahashgraph.api.proto.java.ConsensusUpdateTopicTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAddLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoApproveAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteLiveHashTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.NodeStakeUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleCreateTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ScheduleSignTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenAssociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenBurnTransactionBody;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenGrantKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TokenPauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenRevokeKycTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnpauseTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.TokenWipeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.hederahashgraph.api.proto.java.UtilPrngTransactionBody;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionDispatcherTest {

    @Mock(strictness = LENIENT)
    private HederaState state;

    @Mock(strictness = LENIENT)
    private ReadableAccountStore accountStore;

    @Mock
    private ConsensusCreateTopicHandler consensusCreateTopicHandler;

    @Mock
    private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;

    @Mock
    private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;

    @Mock
    private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock
    private ContractCreateHandler contractCreateHandler;

    @Mock
    private ContractUpdateHandler contractUpdateHandler;

    @Mock
    private ContractCallHandler contractCallHandler;

    @Mock
    private ContractDeleteHandler contractDeleteHandler;

    @Mock
    private ContractSystemDeleteHandler contractSystemDeleteHandler;

    @Mock
    private ContractSystemUndeleteHandler contractSystemUndeleteHandler;

    @Mock
    private EtherumTransactionHandler etherumTransactionHandler;

    @Mock
    private CryptoCreateHandler cryptoCreateHandler;

    @Mock
    private CryptoUpdateHandler cryptoUpdateHandler;

    @Mock
    private CryptoTransferHandler cryptoTransferHandler;

    @Mock
    private CryptoDeleteHandler cryptoDeleteHandler;

    @Mock
    private CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;

    @Mock
    private CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;

    @Mock
    private CryptoAddLiveHashHandler cryptoAddLiveHashHandler;

    @Mock
    private CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock
    private FileCreateHandler fileCreateHandler;

    @Mock
    private FileUpdateHandler fileUpdateHandler;

    @Mock
    private FileDeleteHandler fileDeleteHandler;

    @Mock
    private FileAppendHandler fileAppendHandler;

    @Mock
    private FileSystemDeleteHandler fileSystemDeleteHandler;

    @Mock
    private FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock
    private FreezeHandler freezeHandler;

    @Mock
    private NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    @Mock
    private ScheduleCreateHandler scheduleCreateHandler;

    @Mock
    private ScheduleSignHandler scheduleSignHandler;

    @Mock
    private ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock
    private TokenCreateHandler tokenCreateHandler;

    @Mock
    private TokenUpdateHandler tokenUpdateHandler;

    @Mock
    private TokenMintHandler tokenMintHandler;

    @Mock
    private TokenBurnHandler tokenBurnHandler;

    @Mock
    private TokenDeleteHandler tokenDeleteHandler;

    @Mock
    private TokenAccountWipeHandler tokenAccountWipeHandler;

    @Mock
    private TokenFreezeAccountHandler tokenFreezeAccountHandler;

    @Mock
    private TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;

    @Mock
    private TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;

    @Mock
    private TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;

    @Mock
    private TokenAssociateToAccountHandler tokenAssociateToAccountHandler;

    @Mock
    private TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;

    @Mock
    private TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;

    @Mock
    private TokenPauseHandler tokenPauseHandler;

    @Mock
    private TokenUnpauseHandler tokenUnpauseHandler;

    @Mock
    private UtilPrngHandler utilPrngHandler;

    @Mock
    private HederaAccountNumbers accountNumbers;

    private TransactionHandlers handlers;
    private TransactionDispatcher dispatcher;

    @SuppressWarnings("JUnitMalformedDeclaration")
    @BeforeEach
    void setup(@Mock final ReadableStates readableStates, @Mock HederaKey payerKey) {
        when(state.createReadableStates(any())).thenReturn(readableStates);
        when(accountStore.getKey(any(AccountID.class))).thenReturn(KeyOrLookupFailureReason.withKey(payerKey));

        handlers = new TransactionHandlers(
                consensusCreateTopicHandler,
                consensusUpdateTopicHandler,
                consensusDeleteTopicHandler,
                consensusSubmitMessageHandler,
                contractCreateHandler,
                contractUpdateHandler,
                contractCallHandler,
                contractDeleteHandler,
                contractSystemDeleteHandler,
                contractSystemUndeleteHandler,
                etherumTransactionHandler,
                cryptoCreateHandler,
                cryptoUpdateHandler,
                cryptoTransferHandler,
                cryptoDeleteHandler,
                cryptoApproveAllowanceHandler,
                cryptoDeleteAllowanceHandler,
                cryptoAddLiveHashHandler,
                cryptoDeleteLiveHashHandler,
                fileCreateHandler,
                fileUpdateHandler,
                fileDeleteHandler,
                fileAppendHandler,
                fileSystemDeleteHandler,
                fileSystemUndeleteHandler,
                freezeHandler,
                networkUncheckedSubmitHandler,
                scheduleCreateHandler,
                scheduleSignHandler,
                scheduleDeleteHandler,
                tokenCreateHandler,
                tokenUpdateHandler,
                tokenMintHandler,
                tokenBurnHandler,
                tokenDeleteHandler,
                tokenAccountWipeHandler,
                tokenFreezeAccountHandler,
                tokenUnfreezeAccountHandler,
                tokenGrantKycToAccountHandler,
                tokenRevokeKycFromAccountHandler,
                tokenAssociateToAccountHandler,
                tokenDissociateFromAccountHandler,
                tokenFeeScheduleUpdateHandler,
                tokenPauseHandler,
                tokenUnpauseHandler,
                utilPrngHandler);

        dispatcher = new TransactionDispatcher(handlers, accountNumbers);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new TransactionDispatcher(null, accountNumbers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(handlers, null)).isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var validContext = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .setFileCreate(FileCreateTransactionBody.getDefaultInstance())
                        .build(),
                payer);
        final var invalidSystemDelete = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .setSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
                        .build(),
                payer);
        final var invalidSystemUndelete = new PreHandleContext(
                accountStore,
                TransactionBody.newBuilder()
                        .setSystemUndelete(SystemUndeleteTransactionBody.getDefaultInstance())
                        .build(),
                payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(null, validContext))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, null)).isInstanceOf(NullPointerException.class);

        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemDelete))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, invalidSystemUndelete))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDataNotSetFails() {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, context))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testNodeStakeUpdateFails() {
        // given
        final var txBody = TransactionBody.newBuilder()
                .setNodeStakeUpdate(NodeStakeUpdateTransactionBody.getDefaultInstance())
                .build();
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(tracker, context))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testPreHandleWithPayer(
            final TransactionBody txBody, final BiConsumer<TransactionHandlers, PreHandleContext> verification) {
        // given
        final var payer = AccountID.newBuilder().build();
        final var tracker = new ReadableStoreFactory(state);
        final var context = new PreHandleContext(accountStore, txBody, payer);

        // when
        dispatcher.dispatchPreHandle(tracker, context);

        // then
        verification.accept(this.handlers, context);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusCreateTopic(ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusCreateTopicHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusUpdateTopic(ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusUpdateTopicHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusDeleteTopic(ConsensusDeleteTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusDeleteTopicHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusSubmitMessage(ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.consensusSubmitMessageHandler()).preHandle(eq(meta), any())),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCreateInstance(ContractCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractUpdateInstance(ContractUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractUpdateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractCallHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractDeleteInstance(ContractDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setEthereumTransaction(EthereumTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.etherumTransactionHandler()).preHandle(meta)),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoUpdateAccount(CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoUpdateHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoTransferHandler()).preHandle(eq(meta), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoApproveAllowance(CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoApproveAllowanceHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteAllowance(CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoDeleteAllowanceHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoAddLiveHash(CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoAddLiveHashHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteLiveHash(CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.cryptoDeleteLiveHashHandler()).preHandle(meta)),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileUpdateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileAppendHandler()).preHandle(meta)),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFreeze(FreezeTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.freezeHandler()).preHandle(meta)),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.networkUncheckedSubmitHandler()).preHandle(meta)),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleCreate(ScheduleCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.scheduleCreateHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleSign(ScheduleSignTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.scheduleSignHandler()).preHandle(eq(meta), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleDelete(ScheduleDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.scheduleDeleteHandler()).preHandle(eq(meta), any())),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenCreateHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenUpdateHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenMintHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenBurnHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenDeleteHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenAccountWipeHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFreeze(TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenFreezeAccountHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnfreeze(TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenUnfreezeAccountHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenGrantKyc(TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenGrantKycToAccountHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenRevokeKyc(TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>)
                                (handlers, meta) -> verify(handlers.tokenRevokeKycFromAccountHandler())
                                        .preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenAssociate(TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>)
                                (handlers, meta) -> verify(handlers.tokenAssociateToAccountHandler())
                                        .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDissociate(TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>)
                                (handlers, meta) -> verify(handlers.tokenDissociateFromAccountHandler())
                                        .preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFeeScheduleUpdate(TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenFeeScheduleUpdateHandler()).preHandle(eq(meta), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenPauseHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.tokenUnpauseHandler()).preHandle(meta)),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUtilPrng(UtilPrngTransactionBody.getDefaultInstance())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.utilPrngHandler()).preHandle(meta)),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(SystemDeleteTransactionBody.newBuilder()
                                        .setContractID(ContractID.getDefaultInstance())
                                        .build())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractSystemDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(SystemDeleteTransactionBody.newBuilder()
                                        .setFileID(FileID.getDefaultInstance())
                                        .build())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileSystemDeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(SystemUndeleteTransactionBody.newBuilder()
                                        .setContractID(ContractID.getDefaultInstance())
                                        .build())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.contractSystemUndeleteHandler()).preHandle(meta)),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(SystemUndeleteTransactionBody.newBuilder()
                                        .setFileID(FileID.getDefaultInstance())
                                        .build())
                                .build(),
                        (BiConsumer<TransactionHandlers, PreHandleContext>) (handlers, meta) ->
                                verify(handlers.fileSystemUndeleteHandler()).preHandle(meta)));
    }
}
