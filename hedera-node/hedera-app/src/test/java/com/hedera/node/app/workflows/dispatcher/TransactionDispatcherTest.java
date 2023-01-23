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
import com.hedera.node.app.service.network.impl.handlers.UncheckedSubmitHandler;
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
import com.hederahashgraph.api.proto.java.*;
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

    @Mock private UncheckedSubmitHandler uncheckedSubmitHandler;

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
                        uncheckedSubmitHandler,
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
                        .setConsensusCreateTopic(
                                ConsensusCreateTopicTransactionBody.getDefaultInstance())
                        .build();
        final var payer = AccountID.newBuilder().build();
        final var invalidSystemDelete =
                TransactionBody.newBuilder()
                        .setSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
                        .build();
        final var invalidSystemUndelete =
                TransactionBody.newBuilder()
                        .setSystemUndelete(SystemUndeleteTransactionBody.getDefaultInstance())
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
                                .setConsensusCreateTopic(
                                        ConsensusCreateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusCreateTopicHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusUpdateTopic(
                                        ConsensusUpdateTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusUpdateTopicHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusDeleteTopic(
                                        ConsensusDeleteTopicTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusDeleteTopicHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setConsensusSubmitMessage(
                                        ConsensusSubmitMessageTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.consensusSubmitMessageHandler())
                                                .preHandle(any(), any())),

                // contract
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCreateInstance(
                                        ContractCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractCreateHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractUpdateInstance(
                                        ContractUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.contractUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractCall(ContractCallTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractCallHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setContractDeleteInstance(
                                        ContractDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.contractDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setEthereumTransaction(
                                        EthereumTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.etherumTransactionHandler()).preHandle(any(), any())),

                // crypto
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoCreateAccount(
                                        CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoCreateHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoUpdateHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoTransfer(
                                        CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoTransferHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoApproveAllowanceHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteAllowanceHandler())
                                                .preHandle(any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.cryptoAddLiveHashHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setCryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.cryptoDeleteLiveHashHandler())
                                                .preHandle(any(), any())),

                // file
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileAppendHandler()).preHandle(any(), any())),

                // freeze
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setFreeze(FreezeTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.freezeHandler()).preHandle(any(), any())),

                // network
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUncheckedSubmit(UncheckedSubmitBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.uncheckedSubmitHandler()).preHandle(any(), any())),

                // schedule
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleCreate(
                                        ScheduleCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.scheduleCreateHandler())
                                                .preHandle(any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleSign(ScheduleSignTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.scheduleSignHandler())
                                                .preHandle(any(), any(), any(), any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setScheduleDelete(
                                        ScheduleDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.scheduleDeleteHandler()).preHandle(any(), any())),

                // token
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenCreateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenUpdateHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenMintHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenBurnHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenWipe(TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenAccountWipeHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFreeze(
                                        TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenFreezeAccountHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenUnfreezeAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenGrantKyc(TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenGrantKycToAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenRevokeKycFromAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenAssociate(
                                        TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenAssociateToAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenDissociate(
                                        TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenDissociateFromAccountHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.tokenFeeScheduleUpdateHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenPauseHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.tokenUnpauseHandler()).preHandle(any(), any())),

                // util
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setUtilPrng(UtilPrngTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.utilPrngHandler()).preHandle(any(), any())),

                // mixed
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractSystemDeleteHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemDelete(
                                        SystemDeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h -> verify(h.fileSystemDeleteHandler()).preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setContractID(ContractID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.contractSystemUndeleteHandler())
                                                .preHandle(any(), any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .setSystemUndelete(
                                        SystemUndeleteTransactionBody.newBuilder()
                                                .setFileID(FileID.getDefaultInstance())
                                                .build())
                                .build(),
                        (Consumer<TransactionHandlers>)
                                h ->
                                        verify(h.fileSystemUndeleteHandler())
                                                .preHandle(any(), any())));
    }
}
