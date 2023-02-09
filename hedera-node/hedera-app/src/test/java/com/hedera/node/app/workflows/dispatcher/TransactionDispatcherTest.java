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
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusDeleteTopicTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusSubmitMessageTransactionBody;
import com.hedera.hapi.node.consensus.ConsensusUpdateTopicTransactionBody;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.ContractDeleteTransactionBody;
import com.hedera.hapi.node.contract.ContractUpdateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.file.FileAppendTransactionBody;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.file.FileDeleteTransactionBody;
import com.hedera.hapi.node.file.FileUpdateTransactionBody;
import com.hedera.hapi.node.file.SystemDeleteTransactionBody;
import com.hedera.hapi.node.file.SystemUndeleteTransactionBody;
import com.hedera.hapi.node.freeze.FreezeTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleCreateTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleDeleteTransactionBody;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.token.CryptoAddLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoApproveAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteAllowanceTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteLiveHashTransactionBody;
import com.hedera.hapi.node.token.CryptoDeleteTransactionBody;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenBurnTransactionBody;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.token.TokenDeleteTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.token.TokenFeeScheduleUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenFreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenGrantKycTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenPauseTransactionBody;
import com.hedera.hapi.node.token.TokenRevokeKycTransactionBody;
import com.hedera.hapi.node.token.TokenUnfreezeAccountTransactionBody;
import com.hedera.hapi.node.token.TokenUnpauseTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.token.TokenWipeAccountTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.hapi.node.util.UtilPrngTransactionBody;
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
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.StoreCache;
import java.util.function.Consumer;
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

    @Mock private StoreCache storeCache;
    @Mock private HederaState state;

    @Mock private ConsensusCreateTopicHandler consensusCreateTopicHandler;
    @Mock private ConsensusUpdateTopicHandler consensusUpdateTopicHandler;
    @Mock private ConsensusDeleteTopicHandler consensusDeleteTopicHandler;
    @Mock private ConsensusSubmitMessageHandler consensusSubmitMessageHandler;

    @Mock private ContractCreateHandler contractCreateHandler;
    @Mock private ContractUpdateHandler contractUpdateHandler;
    @Mock private ContractCallHandler contractCallHandler;
    @Mock private ContractDeleteHandler contractDeleteHandler;
    @Mock private ContractSystemDeleteHandler contractSystemDeleteHandler;
    @Mock private ContractSystemUndeleteHandler contractSystemUndeleteHandler;
    @Mock private EtherumTransactionHandler etherumTransactionHandler;

    @Mock private CryptoCreateHandler cryptoCreateHandler;
    @Mock private CryptoUpdateHandler cryptoUpdateHandler;
    @Mock private CryptoTransferHandler cryptoTransferHandler;
    @Mock private CryptoDeleteHandler cryptoDeleteHandler;
    @Mock private CryptoApproveAllowanceHandler cryptoApproveAllowanceHandler;
    @Mock private CryptoDeleteAllowanceHandler cryptoDeleteAllowanceHandler;
    @Mock private CryptoAddLiveHashHandler cryptoAddLiveHashHandler;
    @Mock private CryptoDeleteLiveHashHandler cryptoDeleteLiveHashHandler;

    @Mock private FileCreateHandler fileCreateHandler;
    @Mock private FileUpdateHandler fileUpdateHandler;
    @Mock private FileDeleteHandler fileDeleteHandler;
    @Mock private FileAppendHandler fileAppendHandler;
    @Mock private FileSystemDeleteHandler fileSystemDeleteHandler;
    @Mock private FileSystemUndeleteHandler fileSystemUndeleteHandler;

    @Mock private FreezeHandler freezeHandler;

    @Mock private NetworkUncheckedSubmitHandler networkUncheckedSubmitHandler;

    @Mock private ScheduleCreateHandler scheduleCreateHandler;
    @Mock private ScheduleSignHandler scheduleSignHandler;
    @Mock private ScheduleDeleteHandler scheduleDeleteHandler;

    @Mock private TokenCreateHandler tokenCreateHandler;
    @Mock private TokenUpdateHandler tokenUpdateHandler;
    @Mock private TokenMintHandler tokenMintHandler;
    @Mock private TokenBurnHandler tokenBurnHandler;
    @Mock private TokenDeleteHandler tokenDeleteHandler;
    @Mock private TokenAccountWipeHandler tokenAccountWipeHandler;
    @Mock private TokenFreezeAccountHandler tokenFreezeAccountHandler;
    @Mock private TokenUnfreezeAccountHandler tokenUnfreezeAccountHandler;
    @Mock private TokenGrantKycToAccountHandler tokenGrantKycToAccountHandler;
    @Mock private TokenRevokeKycFromAccountHandler tokenRevokeKycFromAccountHandler;
    @Mock private TokenAssociateToAccountHandler tokenAssociateToAccountHandler;
    @Mock private TokenDissociateFromAccountHandler tokenDissociateFromAccountHandler;
    @Mock private TokenFeeScheduleUpdateHandler tokenFeeScheduleUpdateHandler;
    @Mock private TokenPauseHandler tokenPauseHandler;
    @Mock private TokenUnpauseHandler tokenUnpauseHandler;

    @Mock private UtilPrngHandler utilPrngHandler;

    @Mock private HederaAccountNumbers numbers;
    @Mock private HederaFileNumbers fileNumbers;
    @Mock private AccountKeyLookup keyLookup;
    private PreHandleContext preHandleCtx;

    private TransactionHandlers handlers;
    private TransactionDispatcher dispatcher;

    @BeforeEach
    void setup() {
        handlers =
                new TransactionHandlers(
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

        preHandleCtx = new PreHandleContext(numbers, fileNumbers, keyLookup);
        dispatcher = new TransactionDispatcher(handlers, storeCache, preHandleCtx);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new TransactionDispatcher(null, storeCache, preHandleCtx))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TransactionDispatcher(handlers, null, preHandleCtx))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testDispatchWithIllegalParameters() {
        // given
        final var txBody =
                TransactionBody.newBuilder()
                        .consensusCreateTopic(
                                ConsensusCreateTopicTransactionBody.newBuilder().build())
                        .build();
        final var payer = AccountID.newBuilder().build();
        final var invalidSystemDelete =
                TransactionBody.newBuilder()
                        .systemDelete(SystemDeleteTransactionBody.newBuilder().build())
                        .build();
        final var invalidSystemUndelete =
                TransactionBody.newBuilder()
                        .systemUndelete(SystemUndeleteTransactionBody.newBuilder().build())
                        .build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(null, txBody, payer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, null, payer))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, txBody, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, invalidSystemDelete, payer))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, invalidSystemUndelete, payer))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testDataNotSetFails() {
        // given
        final var txBody = TransactionBody.newBuilder().build();
        final var payer = AccountID.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, txBody, payer))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void testNodeStakeUpdateFails() {
        // given
        final var txBody =
                TransactionBody.newBuilder()
                        .setNodeStakeUpdate(NodeStakeUpdateTransactionBody.getDefaultInstance())
                        .build();
        final var payer = AccountID.newBuilder().build();

        // then
        assertThatThrownBy(() -> dispatcher.dispatchPreHandle(state, txBody, payer))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testPreHandleWithPayer(
            final TransactionBody txBody, final Consumer<TransactionHandlers> verification) {
        // given
        final var payer = AccountID.newBuilder().build();

        // when
        dispatcher.dispatchPreHandle(state, txBody, payer);

        // then
        verification.accept(this.handlers);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                // consensus
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusCreateTopic(
                                        ConsensusCreateTopicTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusCreateTopicHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusUpdateTopic(
                                        ConsensusUpdateTopicTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusUpdateTopicHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusDeleteTopic(
                                        ConsensusDeleteTopicTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusDeleteTopicHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .consensusSubmitMessage(
                                        ConsensusSubmitMessageTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusSubmitMessageHandler())
                                                .preHandle(any(), any())),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .contractCreateInstance(
                                        ContractCreateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractCreateHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .contractUpdateInstance(
                                        ContractUpdateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractUpdateHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .contractCall(ContractCallTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractCallHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .contractDeleteInstance(
                                        ContractDeleteTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractDeleteHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .ethereumTransaction(
                                        EthereumTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.etherumTransactionHandler()).preHandle(any(), any())),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoCreateAccount(
                                        CryptoCreateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoCreateHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoUpdateHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoTransfer(
                                        CryptoTransferTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoTransferHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoDelete(CryptoDeleteTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoApproveAllowanceHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteAllowanceHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.cryptoAddLiveHashHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .cryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteLiveHashHandler())
                                                .preHandle(any(), any())),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .fileCreate(FileCreateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .fileUpdate(FileUpdateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .fileDelete(FileDeleteTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .fileAppend(FileAppendTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileAppendHandler()).preHandle(any(), any())),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .freeze(FreezeTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.freezeHandler()).preHandle(any(), any())),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .uncheckedSubmit(UncheckedSubmitBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.networkUncheckedSubmitHandler())
                                                .preHandle(any(), any())),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .scheduleCreate(
                                        ScheduleCreateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.scheduleCreateHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .scheduleSign(ScheduleSignTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.scheduleSignHandler())
                                                .preHandle(any(), any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .scheduleDelete(
                                        ScheduleDeleteTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.scheduleDeleteHandler())
                                                .preHandle(any(), any(), any(), any())),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenCreation(TokenCreateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenCreateHandler()).preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenUpdate(TokenUpdateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenMint(TokenMintTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenMintHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenBurn(TokenBurnTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenBurnHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenDeletion(TokenDeleteTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenWipe(TokenWipeAccountTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenAccountWipeHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenFreeze(
                                        TokenFreezeAccountTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenFreezeAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenUnfreezeAccountHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenGrantKyc(TokenGrantKycTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenGrantKycToAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenRevokeKycFromAccountHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenAssociate(
                                        TokenAssociateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenAssociateToAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenDissociate(
                                        TokenDissociateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenDissociateFromAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenFeeScheduleUpdateHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenPause(TokenPauseTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenPauseHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .tokenUnpause(TokenUnpauseTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenUnpauseHandler()).preHandle(any(), any())),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .utilPrng(UtilPrngTransactionBody.newBuilder().build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.utilPrngHandler()).preHandle(any(), any())),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .systemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .contractID(ContractID.newBuilder().build())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractSystemDeleteHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .systemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .fileID(FileID.newBuilder().build())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileSystemDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .systemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .contractID(ContractID.newBuilder().build())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractSystemUndeleteHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .systemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .fileID(FileID.newBuilder().build())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.fileSystemUndeleteHandler())
                                                .preHandle(any(), any())));
    }
}
