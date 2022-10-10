/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.token;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_MINT_AMOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.METADATA_TOO_LONG;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.state.validation.UsageLimits;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenMintTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TokenMintTransitionLogicTest {
    private final long amount = 123L;
    private final TokenID grpcId = IdUtils.asToken("1.2.3");

    @Mock private TransactionContext txnCtx;
    @Mock private SignedTxnAccessor accessor;
    @Mock private TransactionBody transactionBody;
    @Mock private TokenMintTransactionBody mintTransactionBody;
    @Mock private OptionValidator validator;
    @Mock private UsageLimits usageLimits;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private TypedTokenStore tokenStore;
    @Mock private AccountStore accountStore;

    private TransactionBody tokenMintTxn;

    private MintLogic mintLogic;
    private TokenMintTransitionLogic subject;

    @BeforeEach
    void setup() {
        mintLogic =
                new MintLogic(usageLimits, validator, tokenStore, accountStore, dynamicProperties);
        subject = new TokenMintTransitionLogic(txnCtx, mintLogic);
    }

    @Test
    void rejectsUniqueWhenNftsNotEnabled() {
        givenValidUniqueTxnCtx();
        given(dynamicProperties.areNftsEnabled()).willReturn(false);

        // expect:
        assertEquals(NOT_SUPPORTED, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx();

        // expect:
        assertTrue(subject.applicability().test(tokenMintTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    @Test
    void acceptsValidTxn() {
        givenValidTxnCtx();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void rejectsMissingToken() {
        givenMissingToken();

        // expect:
        assertEquals(INVALID_TOKEN_ID, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void rejectsInvalidNegativeAmount() {
        givenInvalidNegativeAmount();

        // expect:
        assertEquals(INVALID_TOKEN_MINT_AMOUNT, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void allowsZeroAmount() {
        givenZeroAmount();

        // expect:
        assertEquals(OK, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void rejectsInvalidTxnBody() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(amount)
                                        .addAllMetadata(List.of(ByteString.copyFromUtf8("memo"))))
                        .build();

        assertEquals(INVALID_TRANSACTION_BODY, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void allowsTxnBodyWithNoProps() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(TokenMintTransactionBody.newBuilder().setToken(grpcId))
                        .build();

        assertEquals(OK, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void propagatesErrorOnBadMetadata() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .addAllMetadata(
                                                List.of(
                                                        ByteString.copyFromUtf8(""),
                                                        ByteString.EMPTY))
                                        .setToken(grpcId))
                        .build();
        given(validator.maxBatchSizeMintCheck(tokenMintTxn.getTokenMint().getMetadataCount()))
                .willReturn(OK);
        given(validator.nftMetadataCheck(any())).willReturn(METADATA_TOO_LONG);
        assertEquals(METADATA_TOO_LONG, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void propagatesErrorOnMaxBatchSizeReached() {
        given(dynamicProperties.areNftsEnabled()).willReturn(true);
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .addAllMetadata(List.of(ByteString.copyFromUtf8("")))
                                        .setToken(grpcId))
                        .build();

        given(validator.maxBatchSizeMintCheck(tokenMintTxn.getTokenMint().getMetadataCount()))
                .willReturn(BATCH_SIZE_LIMIT_EXCEEDED);
        assertEquals(BATCH_SIZE_LIMIT_EXCEEDED, subject.semanticCheck().apply(tokenMintTxn));
    }

    @Test
    void callsMintLogicWithCorrectParams() {
        mintLogic = mock(MintLogic.class);
        subject = new TokenMintTransitionLogic(txnCtx, mintLogic);

        var consensus = Instant.now();
        var grpcId = IdUtils.asToken("0.0.1");
        var amount = 4321L;
        var count = 3;
        List<ByteString> metadataList =
                List.of(ByteString.copyFromUtf8("test"), ByteString.copyFromUtf8("test test"));
        given(txnCtx.accessor()).willReturn(accessor);
        given(txnCtx.consensusTime()).willReturn(consensus);
        given(accessor.getTxn()).willReturn(transactionBody);
        given(transactionBody.getTokenMint()).willReturn(mintTransactionBody);
        given(mintTransactionBody.getToken()).willReturn(grpcId);
        given(mintTransactionBody.getAmount()).willReturn(amount);
        given(mintTransactionBody.getMetadataCount()).willReturn(count);
        given(mintTransactionBody.getMetadataList()).willReturn(metadataList);
        subject.doStateTransition();

        verify(mintLogic).mint(Id.fromGrpcToken(grpcId), count, amount, metadataList, consensus);
    }

    private void givenValidTxnCtx() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(amount))
                        .build();
    }

    private void givenValidUniqueTxnCtx() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .addAllMetadata(List.of(ByteString.copyFromUtf8("memo"))))
                        .build();
    }

    private void givenMissingToken() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(TokenMintTransactionBody.newBuilder().build())
                        .build();
    }

    private void givenInvalidNegativeAmount() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(-1)
                                        .build())
                        .build();
    }

    private void givenZeroAmount() {
        tokenMintTxn =
                TransactionBody.newBuilder()
                        .setTokenMint(
                                TokenMintTransactionBody.newBuilder()
                                        .setToken(grpcId)
                                        .setAmount(0)
                                        .build())
                        .build();
    }
}
