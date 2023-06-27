/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.test.handlers;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hedera.node.app.service.mono.grpc.marshalling.AssessedCustomFeeWrapper;
import com.hedera.node.app.service.mono.grpc.marshalling.CustomFeeMeta;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.state.submerkle.FcAssessedCustomFee;
import com.hedera.node.app.service.mono.state.submerkle.FcCustomFee;
import com.hedera.node.app.service.mono.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFromWithAllowance;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.hbarChange;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

public class CryptoTransferHandlerTest {
//    @Test
//    void happyPathUsesLedgerNetZero() {
//        final var a = asAccount("1.2.3");
//        final var b = asAccount("2.3.4");
//        final var impliedTransfers = ImpliedTransfers.valid(
//                validationProps,
//                List.of(hbarChange(a, +100), hbarChange(b, -100)),
//                new ArrayList<>(),
//                new ArrayList<>());
//
//        givenValidTxnCtx();
//        // and:
//        given(spanMapAccessor.getImpliedTransfers(accessor)).willReturn(impliedTransfers);
//
//        doThrow(new InvalidTransactionException(INSUFFICIENT_ACCOUNT_BALANCE))
//                .when(ledger)
//                .doZeroSum(anyList());
//
//        // when:
//        assertFailsWith(() -> subject.doStateTransition(), INSUFFICIENT_ACCOUNT_BALANCE);
//    }
//
//    @Test
//    void recomputesImpliedTransfersIfNotAvailableInSpan() {
//        final var a = AccountID.newBuilder()
//                .setShardNum(0)
//                .setRealmNum(0)
//                .setAlias(ByteString.copyFromUtf8("aaaa"))
//                .build();
//        final var b = asAccount("2.3.4");
//        final var impliedTransfers = ImpliedTransfers.valid(
//                validationProps,
//                List.of(hbarChange(a, +100), hbarChange(b, -100)),
//                new ArrayList<>(),
//                new ArrayList<>());
//
//        givenValidTxnCtx();
//        given(accessor.getPayer()).willReturn(payer);
//        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
//        // and:
//        given(impliedTransfersMarshal.unmarshalFromGrpc(cryptoTransferTxn.getCryptoTransfer(), payer))
//                .willReturn(impliedTransfers);
//
//        // when:
//        subject.doStateTransition();
//
//        // then:
//        assertDoesNotThrow(() -> subject.doStateTransition());
//    }
//
//    @Test
//    void verifyIfAssessedCustomFeesSet() {
//        // setup :
//        final var a = Id.fromGrpcAccount(asAccount("1.2.3"));
//        final var b = Id.fromGrpcAccount(asAccount("2.3.4"));
//        final var c = Id.fromGrpcToken(asToken("4.5.6"));
//        final var d = Id.fromGrpcToken(asToken("5.6.7"));
//
//        // and :
//        final var customFeesBalanceChangeWrapper =
//                List.of(new AssessedCustomFeeWrapper(a.asEntityId(), 10L, new AccountID[] {
//                        AccountID.newBuilder().setAccountNum(123L).build()
//                }));
//        final var customFee = List.of(FcCustomFee.fixedFee(20L, null, a.asEntityId(), false));
//        final List<CustomFeeMeta> customFees = List.of(new CustomFeeMeta(c, d, customFee));
//        final var impliedTransfers = ImpliedTransfers.valid(
//                validationProps,
//                List.of(hbarChange(a.asGrpcAccount(), +100), hbarChange(b.asGrpcAccount(), -100)),
//                customFees,
//                customFeesBalanceChangeWrapper);
//
//        givenValidTxnCtx();
//        given(accessor.getPayer()).willReturn(payer);
//        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
//        // and:
//        given(impliedTransfersMarshal.unmarshalFromGrpc(cryptoTransferTxn.getCryptoTransfer(), payer))
//                .willReturn(impliedTransfers);
//
//        // when:
//        subject.doStateTransition();
//
//        // then:
//        final var customFeesBalanceChange = List.of(new FcAssessedCustomFee(a.asEntityId(), 10L, new long[] {123L}));
//        verify(txnCtx).setAssessedCustomFees(customFeesBalanceChange);
//    }
//
//    @Test
//    void shortCircuitsToImpliedTransfersValidityIfNotAvailableInSpan() {
//        final var impliedTransfers = ImpliedTransfers.invalid(validationProps, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
//
//        givenValidTxnCtx();
//        given(accessor.getPayer()).willReturn(payer);
//        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
//        // and:
//        given(impliedTransfersMarshal.unmarshalFromGrpc(cryptoTransferTxn.getCryptoTransfer(), payer))
//                .willReturn(impliedTransfers);
//
//        // when & then:
//        assertFailsWith(() -> subject.doStateTransition(), TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
//        // then:
//        verifyNoInteractions(ledger);
//    }
//
//    @Test
//    void reusesPrecomputedFailureIfImpliedTransfersInSpan() {
//        // setup:
//        final var impliedTransfers = ImpliedTransfers.invalid(validationProps, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
//
//        given(spanMapAccessor.getImpliedTransfers(accessor)).willReturn(impliedTransfers);
//
//        // when:
//        final var validity = subject.validateSemantics(accessor);
//
//        // then:
//        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, validity);
//    }
//
//    @Test
//    void doesntAllowAllowanceTransfersWhenNotSupported() {
//        xfers = CryptoTransferTransactionBody.newBuilder()
//                .setTransfers(TransferList.newBuilder()
//                        .addAccountAmounts(adjustFromWithAllowance(asAccount("0.0.75231"), -1_000))
//                        .addAccountAmounts(adjustFromWithAllowance(asAccount("0.0.1000"), +1_000))
//                        .build())
//                .addTokenTransfers(TokenTransferList.newBuilder()
//                        .setToken(asToken("0.0.12345"))
//                        .addAllTransfers(List.of(
//                                adjustFromWithAllowance(asAccount("0.0.2"), -1_000),
//                                adjustFromWithAllowance(asAccount("0.0.2000"), +1_000))))
//                .build();
//        cryptoTransferTxn =
//                TransactionBody.newBuilder().setCryptoTransfer(xfers).build();
//        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
//        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
//        given(transferSemanticChecks.fullPureValidation(any(), any(), any())).willReturn(NOT_SUPPORTED);
//        final var validity = subject.validateSemantics(accessor);
//        assertEquals(NOT_SUPPORTED, validity);
//    }
//
//    @Test
//    void computesFailureIfImpliedTransfersNotInSpan() {
//        // setup:
//        final var pretendXferTxn = TransactionBody.getDefaultInstance();
//
//        given(dynamicProperties.maxTransferListSize()).willReturn(maxHbarAdjusts);
//        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxTokenAdjusts);
//        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxOwnershipChanges);
//        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
//        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
//        given(dynamicProperties.isAutoCreationEnabled()).willReturn(autoCreationEnabled);
//        given(dynamicProperties.isLazyCreationEnabled()).willReturn(lazyCreationEnabled);
//        given(dynamicProperties.areAllowancesEnabled()).willReturn(areAllowancesEnabled);
//        given(accessor.getTxn()).willReturn(pretendXferTxn);
//        given(transferSemanticChecks.fullPureValidation(
//                pretendXferTxn.getCryptoTransfer().getTransfers(),
//                pretendXferTxn.getCryptoTransfer().getTokenTransfersList(),
//                validationProps))
//                .willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
//
//        // when:
//        final var validity = subject.validateSemantics(accessor);
//
//        // then:
//        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, validity);
//    }
//private void givenTxn(){
//    final var transactionID = TransactionID.newBuilder().accountID(payerId).transactionValidStart(consensusTimestamp);
//    txn = CryptoTransferTransactionBody.newBuilder()
//            .transfers(TransferList.newBuilder()
//                    .accountAmounts(adjustFrom(transferAccountId, -1_000))
//                    .accountAmounts(adjustFrom(deleteAccountId, +1_000))
//                    .build())
//            .tokenTransfers(TokenTransferList.newBuilder()
//                    .token(fungibleTokenId)
//                    .transfers(List.of(
//                            adjustFrom(ownerId, -1_000),
//                            adjustFrom(spenderId, +1_000)))
//                    .build())
//            .build();
//}
}
