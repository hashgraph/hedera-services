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

package com.hedera.node.app.service.token.impl.test.handlers;


import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.fixtures.workflows.ExceptionConditions.responseCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.config.VersionedConfigImpl;
import com.hedera.node.app.records.SingleTransactionRecordBuilder;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.handlers.TokenMintHandler;
import com.hedera.node.app.service.token.impl.records.TokenMintRecordBuilder;
import com.hedera.node.app.service.token.impl.test.handlers.util.CryptoTokenHandlerTestBase;
import com.hedera.node.app.service.token.impl.validators.TokenSupplyChangeOpsValidator;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.ConfigProvider;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.time.Instant;
import java.util.List;

class TokenMintHandlerTest extends CryptoTokenHandlerTestBase {
    @Mock(strictness = Mock.Strictness.LENIENT)
    private ConfigProvider configProvider;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private HandleContext handleContext;

    private final Bytes metadata1 = Bytes.wrap("memo".getBytes());
    private final Bytes metadata2 = Bytes.wrap("memo2".getBytes());
    private final Instant consensusNow = Instant.ofEpochSecond(1_234_567L);
    private SingleTransactionRecordBuilder recordBuilder;
    private TokenMintHandler subject;

    @BeforeEach
    public void setUp(){
        super.setUp();
        refreshWritableStores();
        givenStoresAndConfig(configProvider, handleContext);
        subject = new TokenMintHandler(new TokenSupplyChangeOpsValidator(configProvider));
        recordBuilder = new SingleTransactionRecordBuilder(consensusNow);
    }

    @Test
    void rejectsNftMintsWhenNftsNotEnabled() {
        givenMintTxn(nonFungibleTokenId, List.of(metadata1, metadata2), null);
        configuration = new HederaTestConfigBuilder()
                .withValue("tokens.nfts.areEnabled", false)
                .getOrCreateConfig();
        given(configProvider.getConfiguration()).willReturn(new VersionedConfigImpl(configuration, 1));
        given(handleContext.configuration()).willReturn(configuration);

        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(NOT_SUPPORTED));
    }

    @Test
    void acceptsValidFungibleTokenMintTxn() {
        givenMintTxn(fungibleTokenId, null, 10L);

        assertThat(writableTokenRelStore.get(treasuryId, fungibleTokenId).balance()).isEqualTo(1000L);
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);
        assertThat(writableAccountStore.get(treasuryId).numberPositiveBalances()).isEqualTo(2);
        assertThat(writableTokenStore.get(fungibleTokenId).totalSupply()).isEqualTo(1000L);

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        // treasury relation balance will increase
        assertThat(writableTokenRelStore.get(treasuryId, fungibleTokenId).balance()).isEqualTo(1010L);
        // tinybar balance should not get affected
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);

        // since there are positive balances for this token relation already, it will not be increased.
        assertThat(writableAccountStore.get(treasuryId).numberPositiveBalances()).isEqualTo(2);
        // supply of fungible token increases
        assertThat(writableTokenStore.get(fungibleTokenId).totalSupply()).isEqualTo(1010L);
    }

    @Test
    void acceptsValidNonFungibleTokenMintTxn() {
        givenMintTxn(nonFungibleTokenId, List.of(metadata1, metadata2), null);

        assertThat(writableTokenRelStore.get(treasuryId, nonFungibleTokenId).balance()).isEqualTo(1000L);
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);
        assertThat(writableAccountStore.get(treasuryId).numberOwnedNfts()).isEqualTo(2);
        assertThat(writableTokenStore.get(nonFungibleTokenId).totalSupply()).isEqualTo(1000L);
        assertThat(recordBuilder.serialNumbers()).isNull();

        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));

        // treasury relation balance will increase by metadata list size
        assertThat(writableTokenRelStore.get(treasuryId, nonFungibleTokenId).balance()).isEqualTo(1002L);
        // tinybar balance should not get affected
        assertThat(writableAccountStore.get(treasuryId).tinybarBalance()).isEqualTo(10000L);

        // number of owned NFTs should increase
        assertThat(writableAccountStore.get(treasuryId).numberOwnedNfts()).isEqualTo(4);
        // treasury relation supply will not increase since its not fungible token change
        assertThat(writableTokenStore.get(nonFungibleTokenId).totalSupply()).isEqualTo(1000L);
        assertThat(recordBuilder.serialNumbers()).isEqualTo(List.of(1L, 2L));
    }

    @Test
    void failsOnMissingToken() {
        givenMintTxn(TokenID.newBuilder().tokenNum(100000L).build(), List.of(metadata1, metadata2), null);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_ID));
    }

    @Test
    void failsOnNegativeAmount() {
        givenMintTxn(fungibleTokenId, null, -2L);
        assertThatThrownBy(() -> subject.handle(handleContext))
                .isInstanceOf(HandleException.class)
                .has(responseCode(INVALID_TOKEN_MINT_AMOUNT));
    }

    @Test
    void allowsZeroAmount() {
        givenMintTxn(fungibleTokenId, null, 0L);
        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
    }

    @Test
    void rejectsBothAMountAndMetadataFields() throws PreCheckException {
        final var txn = givenMintTxn(fungibleTokenId, List.of(metadata1), 2L);
        final var context = new FakePreHandleContext(readableAccountStore, txn);
        assertThatThrownBy(() -> subject.preHandle(context))
                .isInstanceOf(PreCheckException.class)
                .has(responseCode(INVALID_TRANSACTION_BODY));
    }

//    @Test
//    void allowsTxnBodyWithNoProps() throws PreCheckException {
//        final var txn = givenMintTxn(fungibleTokenId, null, null);
//        refreshReadableStores();
//        final var context = new FakePreHandleContext(readableAccountStore, txn);
//
//        assertThatNoException().isThrownBy(() -> subject.preHandle(context));
//        assertThatNoException().isThrownBy(() -> subject.handle(handleContext));
//    }
//
//    @Test
//    void propagatesErrorOnBadMetadata() {
//        given(dynamicProperties.areNftsEnabled()).willReturn(true);
//        tokenMintTxn = TransactionBody.newBuilder()
//                .setTokenMint(TokenMintTransactionBody.newBuilder()
//                        .addAllMetadata(List.of(ByteString.copyFromUtf8(""), ByteString.EMPTY))
//                        .setToken(grpcId))
//                .build();
//        given(validator.maxBatchSizeMintCheck(tokenMintTxn.getTokenMint().getMetadataCount()))
//                .willReturn(OK);
//        given(validator.nftMetadataCheck(any())).willReturn(METADATA_TOO_LONG);
//        assertEquals(METADATA_TOO_LONG, subject.semanticCheck().apply(tokenMintTxn));
//    }
//
//    @Test
//    void propagatesErrorOnMaxBatchSizeReached() {
//        given(dynamicProperties.areNftsEnabled()).willReturn(true);
//        tokenMintTxn = TransactionBody.newBuilder()
//                .setTokenMint(TokenMintTransactionBody.newBuilder()
//                        .addAllMetadata(List.of(ByteString.copyFromUtf8("")))
//                        .setToken(grpcId))
//                .build();
//
//        given(validator.maxBatchSizeMintCheck(tokenMintTxn.getTokenMint().getMetadataCount()))
//                .willReturn(BATCH_SIZE_LIMIT_EXCEEDED);
//        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.semanticCheck().apply(tokenMintTxn));
//    }
//
//    @Test
//    void callsMintLogicWithCorrectParams() {
//        mintLogic = mock(MintLogic.class);
//        subject = new TokenMintTransitionLogic(txnCtx, mintLogic);
//
//        var consensus = Instant.now();
//        var grpcId = IdUtils.asToken("0.0.1");
//        var amount = 4321L;
//        var count = 3;
//        List<ByteString> metadataList = List.of(ByteString.copyFromUtf8("test"), ByteString.copyFromUtf8("test test"));
//        given(txnCtx.accessor()).willReturn(accessor);
//        given(txnCtx.consensusTime()).willReturn(consensus);
//        given(accessor.getTxn()).willReturn(transactionBody);
//        given(transactionBody.getTokenMint()).willReturn(mintTransactionBody);
//        given(mintTransactionBody.getToken()).willReturn(grpcId);
//        given(mintTransactionBody.getAmount()).willReturn(amount);
//        given(mintTransactionBody.getMetadataCount()).willReturn(count);
//        given(mintTransactionBody.getMetadataList()).willReturn(metadataList);
//        subject.doStateTransition();
//
//        verify(mintLogic).mint(Id.fromGrpcToken(grpcId), count, amount, metadataList, consensus);
//    }
//
//    @Test
//    void validatesMintCap() {
//        givenValidUniqueTxnCtx();
//        given(accessor.getTxn()).willReturn(tokenMintTxn);
//        given(txnCtx.accessor()).willReturn(accessor);
//        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
//        given(token.getId()).willReturn(id);
//        given(store.loadToken(id)).willReturn(token);
//        willThrow(new InvalidTransactionException(MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED))
//                .given(usageLimits)
//                .assertMintableNfts(1);
//
//        // expect:
//        assertFailsWith(
//                () -> subject.mint(
//                        token.getId(),
//                        txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
//                        txnCtx.accessor().getTxn().getTokenMint().getAmount(),
//                        txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
//                        Instant.now()),
//                MAX_NFTS_IN_PRICE_REGIME_HAVE_BEEN_MINTED);
//    }
//
//    @Test
//    void followsHappyPath() {
//        // setup:
//        treasuryRel = new TokenRelationship(token, treasury);
//
//        givenValidTxnCtx();
//        given(accessor.getTxn()).willReturn(tokenMintTxn);
//        given(txnCtx.accessor()).willReturn(accessor);
//        given(store.loadToken(id)).willReturn(token);
//        given(token.getTreasury()).willReturn(treasury);
//        given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
//        given(token.getType()).willReturn(TokenType.FUNGIBLE_COMMON);
//        given(token.getId()).willReturn(id);
//
//        // when:
//        subject.mint(
//                token.getId(),
//                txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
//                txnCtx.accessor().getTxn().getTokenMint().getAmount(),
//                txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
//                Instant.now());
//
//        // then:
//        verify(token).mint(treasuryRel, amount, false);
//        verify(store).commitToken(token);
//        verify(store).commitTokenRelationships(List.of(treasuryRel));
//    }
//
//    @Test
//    void followsUniqueHappyPath() {
//        treasuryRel = new TokenRelationship(token, treasury);
//
//        givenValidUniqueTxnCtx();
//        given(accessor.getTxn()).willReturn(tokenMintTxn);
//        given(txnCtx.accessor()).willReturn(accessor);
//        given(token.getTreasury()).willReturn(treasury);
//        given(store.loadToken(id)).willReturn(token);
//        given(token.getId()).willReturn(id);
//        given(store.loadTokenRelationship(token, treasury)).willReturn(treasuryRel);
//        given(token.getType()).willReturn(TokenType.NON_FUNGIBLE_UNIQUE);
//        // when:
//        subject.mint(
//                token.getId(),
//                txnCtx.accessor().getTxn().getTokenMint().getMetadataCount(),
//                txnCtx.accessor().getTxn().getTokenMint().getAmount(),
//                txnCtx.accessor().getTxn().getTokenMint().getMetadataList(),
//                Instant.now());
//
//        // then:
//        verify(token).mint(any(OwnershipTracker.class), eq(treasuryRel), any(List.class), any(RichInstant.class));
//        verify(store).commitToken(token);
//        verify(store).commitTokenRelationships(List.of(treasuryRel));
//        verify(store).commitTrackers(any(OwnershipTracker.class));
//        verify(accountStore).commitAccount(any(Account.class));
//    }
//

    private TransactionBody givenMintTxn(
            final TokenID tokenId,
            final List<Bytes> metadata,
            final Long amount) {
        final var transactionID = TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
        final var builder = TokenMintTransactionBody.newBuilder()
                .token(tokenId);
        if(metadata != null) {
            builder.metadata(metadata);
        }
        if(amount != null){
            builder.amount(amount);
        }
        final var txn =  TransactionBody.newBuilder()
                .transactionID(transactionID)
                .tokenMint(builder.build())
                .build();

        given(handleContext.body()).willReturn(txn);
        given(handleContext.consensusNow()).willReturn(Instant.ofEpochSecond(1_234_567L));
        given(handleContext.recordBuilder(TokenMintRecordBuilder.class)).willReturn(recordBuilder);

        return txn;
    }
}
