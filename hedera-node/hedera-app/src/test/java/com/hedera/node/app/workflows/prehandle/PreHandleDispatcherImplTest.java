/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.workflows.prehandle;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.app.ServicesAccessor;
import com.hedera.node.app.service.file.FilePreTransactionHandler;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.CryptoPreTransactionHandler;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenPreTransactionHandler;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.state.HederaState;
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
class PreHandleDispatcherImplTest {

    @Mock private HederaState hederaState;
    @Mock private CryptoService cryptoService;
    @Mock private CryptoPreTransactionHandler cryptoHandler;
    @Mock private FileService fileService;
    @Mock private FilePreTransactionHandler fileHandler;
    @Mock private TokenService tokenService;
    @Mock private TokenPreTransactionHandler tokenHandler;
    private ServicesAccessor servicesAccessor;

    private PreHandleDispatcherImpl dispatcher;

    @BeforeEach
    void setup() {
        servicesAccessor = new ServicesAccessor(cryptoService, fileService, tokenService);

        when(cryptoService.createPreTransactionHandler(any())).thenReturn(cryptoHandler);
        when(fileService.createPreTransactionHandler(any())).thenReturn(fileHandler);
        when(tokenService.createPreTransactionHandler(any())).thenReturn(tokenHandler);

        dispatcher = new PreHandleDispatcherImpl(hederaState, servicesAccessor);
    }

    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new PreHandleDispatcherImpl(null, servicesAccessor))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PreHandleDispatcherImpl(hederaState, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testDispatchWithIllegalParameters() {
        assertThatThrownBy(() -> dispatcher.dispatch(null))
                .isInstanceOf(NullPointerException.class);
    }

    @ParameterizedTest
    @MethodSource("getDispatchParameters")
    void testCryptoCreateAccount(
            final TransactionBody txBody,
            final Consumer<PreHandleDispatcherImplTest> verification) {
        // when
        dispatcher.dispatch(txBody);

        // then
        verification.accept(this);
    }

    private static Stream<Arguments> getDispatchParameters() {
        return Stream.of(
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoCreateAccount(
                                        CryptoCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleCryptoCreate(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoUpdateAccount(
                                        CryptoUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleUpdateAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoTransfer(
                                        CryptoTransferTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleCryptoTransfer(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDelete(CryptoDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleCryptoDelete(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoApproveAllowance(
                                        CryptoApproveAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandleApproveAllowances(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteAllowance(
                                        CryptoDeleteAllowanceTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.cryptoHandler)
                                                .preHandleDeleteAllowances(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoAddLiveHash(
                                        CryptoAddLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleAddLiveHash(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeCryptoDeleteLiveHash(
                                        CryptoDeleteLiveHashTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.cryptoHandler).preHandleDeleteLiveHash(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileCreate(FileCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleCreateFile(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileUpdate(FileUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleUpdateFile(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileDelete(FileDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleDeleteFile(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeFileAppend(FileAppendTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleAppendContent(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemDelete(SystemDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleSystemDelete(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeSystemUndelete(
                                        SystemUndeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.fileHandler).preHandleSystemUndelete(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenCreation(TokenCreateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleCreateToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUpdate(TokenUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleUpdateToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenMint(TokenMintTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleMintToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenBurn(TokenBurnTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleBurnToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDeletion(TokenDeleteTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleDeleteToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenWipe(
                                        TokenWipeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleWipeTokenAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFreeze(
                                        TokenFreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandleFreezeTokenAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnfreeze(
                                        TokenUnfreezeAccountTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandleUnfreezeTokenAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenGrantKyc(
                                        TokenGrantKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandleGrantKycToTokenAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenRevokeKyc(
                                        TokenRevokeKycTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandleRevokeKycFromTokenAccount(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenAssociate(
                                        TokenAssociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleAssociateTokens(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenDissociate(
                                        TokenDissociateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleDissociateTokens(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenFeeScheduleUpdate(
                                        TokenFeeScheduleUpdateTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test ->
                                        verify(test.tokenHandler)
                                                .preHandleUpdateTokenFeeSchedule(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenPause(TokenPauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandlePauseToken(any())),
                Arguments.of(
                        TransactionBody.newBuilder()
                                .mergeTokenUnpause(TokenUnpauseTransactionBody.getDefaultInstance())
                                .build(),
                        (Consumer<PreHandleDispatcherImplTest>)
                                test -> verify(test.tokenHandler).preHandleUnpauseToken(any())));
    }
}
