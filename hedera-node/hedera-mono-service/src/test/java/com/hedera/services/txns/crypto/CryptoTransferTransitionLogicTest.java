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
package com.hedera.services.txns.crypto;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.adjustFromWithAllowance;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hedera.test.utils.IdUtils.hbarChange;
import static com.hedera.test.utils.TxnUtils.withAdjustments;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.grpc.marshalling.CustomFeeMeta;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransferList;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CryptoTransferTransitionLogicTest {
    private final int maxHbarAdjusts = 5;
    private final int maxTokenAdjusts = 10;
    private final int maxOwnershipChanges = 15;
    private final boolean areNftsEnabled = false;
    private final boolean autoCreationEnabled = true;
    private final boolean areAllowancesEnabled = true;
    private final int maxFeeNesting = 20;
    private final int maxBalanceChanges = 20;
    private final ImpliedTransfersMeta.ValidationProps validationProps =
            new ImpliedTransfersMeta.ValidationProps(
                    maxHbarAdjusts,
                    maxTokenAdjusts,
                    maxOwnershipChanges,
                    maxFeeNesting,
                    maxBalanceChanges,
                    areNftsEnabled,
                    autoCreationEnabled,
                    areAllowancesEnabled);
    private final AccountID payer = AccountID.newBuilder().setAccountNum(1_234L).build();
    private final AccountID a = AccountID.newBuilder().setAccountNum(9_999L).build();
    private final AccountID b = AccountID.newBuilder().setAccountNum(8_999L).build();
    private final AccountID c = AccountID.newBuilder().setAccountNum(7_999L).build();

    @Mock private HederaLedger ledger;
    @Mock private TransactionContext txnCtx;
    @Mock private GlobalDynamicProperties dynamicProperties;
    @Mock private ImpliedTransfersMarshal impliedTransfersMarshal;
    @Mock private PureTransferSemanticChecks transferSemanticChecks;
    @Mock private ExpandHandleSpanMapAccessor spanMapAccessor;
    @Mock private SignedTxnAccessor accessor;

    private TransactionBody cryptoTransferTxn;

    private CryptoTransferTransitionLogic subject;

    @BeforeEach
    void setup() {
        subject =
                new CryptoTransferTransitionLogic(
                        ledger,
                        txnCtx,
                        dynamicProperties,
                        impliedTransfersMarshal,
                        transferSemanticChecks,
                        spanMapAccessor);
    }

    @Test
    void happyPathUsesLedgerNetZero() {
        final var a = asAccount("1.2.3");
        final var b = asAccount("2.3.4");
        final var impliedTransfers =
                ImpliedTransfers.valid(
                        validationProps,
                        List.of(hbarChange(a, +100), hbarChange(b, -100)),
                        new ArrayList<>(),
                        new ArrayList<>());

        givenValidTxnCtx();
        // and:
        given(spanMapAccessor.getImpliedTransfers(accessor)).willReturn(impliedTransfers);

        doThrow(new InvalidTransactionException(INSUFFICIENT_ACCOUNT_BALANCE))
                .when(ledger)
                .doZeroSum(anyList());

        // when:
        assertFailsWith(() -> subject.doStateTransition(), INSUFFICIENT_ACCOUNT_BALANCE);
    }

    @Test
    void recomputesImpliedTransfersIfNotAvailableInSpan() {
        final var a =
                AccountID.newBuilder()
                        .setShardNum(0)
                        .setRealmNum(0)
                        .setAlias(ByteString.copyFromUtf8("aaaa"))
                        .build();
        final var b = asAccount("2.3.4");
        final var impliedTransfers =
                ImpliedTransfers.valid(
                        validationProps,
                        List.of(hbarChange(a, +100), hbarChange(b, -100)),
                        new ArrayList<>(),
                        new ArrayList<>());

        givenValidTxnCtx();
        given(accessor.getPayer()).willReturn(payer);
        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
        // and:
        given(
                        impliedTransfersMarshal.unmarshalFromGrpc(
                                cryptoTransferTxn.getCryptoTransfer(), payer))
                .willReturn(impliedTransfers);

        // when:
        subject.doStateTransition();

        // then:
        assertDoesNotThrow(() -> subject.doStateTransition());
    }

    @Test
    void verifyIfAssessedCustomFeesSet() {
        // setup :
        final var a = Id.fromGrpcAccount(asAccount("1.2.3"));
        final var b = Id.fromGrpcAccount(asAccount("2.3.4"));
        final var c = Id.fromGrpcToken(asToken("4.5.6"));
        final var d = Id.fromGrpcToken(asToken("5.6.7"));

        // and :
        final var customFeesBalanceChange =
                List.of(new FcAssessedCustomFee(a.asEntityId(), 10L, new long[] {123L}));
        final var customFee = List.of(FcCustomFee.fixedFee(20L, null, a.asEntityId(), false));
        final List<CustomFeeMeta> customFees = List.of(new CustomFeeMeta(c, d, customFee));
        final var impliedTransfers =
                ImpliedTransfers.valid(
                        validationProps,
                        List.of(
                                hbarChange(a.asGrpcAccount(), +100),
                                hbarChange(b.asGrpcAccount(), -100)),
                        customFees,
                        customFeesBalanceChange);

        givenValidTxnCtx();
        given(accessor.getPayer()).willReturn(payer);
        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
        // and:
        given(
                        impliedTransfersMarshal.unmarshalFromGrpc(
                                cryptoTransferTxn.getCryptoTransfer(), payer))
                .willReturn(impliedTransfers);

        // when:
        subject.doStateTransition();

        // then:
        verify(txnCtx).setAssessedCustomFees(customFeesBalanceChange);
    }

    @Test
    void shortCircuitsToImpliedTransfersValidityIfNotAvailableInSpan() {
        final var impliedTransfers =
                ImpliedTransfers.invalid(validationProps, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

        givenValidTxnCtx();
        given(accessor.getPayer()).willReturn(payer);
        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
        // and:
        given(
                        impliedTransfersMarshal.unmarshalFromGrpc(
                                cryptoTransferTxn.getCryptoTransfer(), payer))
                .willReturn(impliedTransfers);

        // when & then:
        assertFailsWith(() -> subject.doStateTransition(), TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);
        // then:
        verifyNoInteractions(ledger);
    }

    @Test
    void reusesPrecomputedFailureIfImpliedTransfersInSpan() {
        // setup:
        final var impliedTransfers =
                ImpliedTransfers.invalid(validationProps, TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

        given(spanMapAccessor.getImpliedTransfers(accessor)).willReturn(impliedTransfers);

        // when:
        final var validity = subject.validateSemantics(accessor);

        // then:
        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, validity);
    }

    @Test
    void doesntAllowAllowanceTransfersWhenNotSupported() {
        xfers =
                CryptoTransferTransactionBody.newBuilder()
                        .setTransfers(
                                TransferList.newBuilder()
                                        .addAccountAmounts(
                                                adjustFromWithAllowance(
                                                        asAccount("0.0.75231"), -1_000))
                                        .addAccountAmounts(
                                                adjustFromWithAllowance(
                                                        asAccount("0.0.1000"), +1_000))
                                        .build())
                        .addTokenTransfers(
                                TokenTransferList.newBuilder()
                                        .setToken(asToken("0.0.12345"))
                                        .addAllTransfers(
                                                List.of(
                                                        adjustFromWithAllowance(
                                                                asAccount("0.0.2"), -1_000),
                                                        adjustFromWithAllowance(
                                                                asAccount("0.0.2000"), +1_000))))
                        .build();
        cryptoTransferTxn = TransactionBody.newBuilder().setCryptoTransfer(xfers).build();
        given(accessor.getTxn()).willReturn(cryptoTransferTxn);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(false);
        given(transferSemanticChecks.fullPureValidation(any(), any(), any()))
                .willReturn(NOT_SUPPORTED);
        final var validity = subject.validateSemantics(accessor);
        assertEquals(NOT_SUPPORTED, validity);
    }

    @Test
    void computesFailureIfImpliedTransfersNotInSpan() {
        // setup:
        final var pretendXferTxn = TransactionBody.getDefaultInstance();

        given(dynamicProperties.maxTransferListSize()).willReturn(maxHbarAdjusts);
        given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxTokenAdjusts);
        given(dynamicProperties.maxNftTransfersLen()).willReturn(maxOwnershipChanges);
        given(dynamicProperties.maxCustomFeeDepth()).willReturn(maxFeeNesting);
        given(dynamicProperties.maxXferBalanceChanges()).willReturn(maxBalanceChanges);
        given(dynamicProperties.isAutoCreationEnabled()).willReturn(autoCreationEnabled);
        given(dynamicProperties.areAllowancesEnabled()).willReturn(areAllowancesEnabled);
        given(accessor.getTxn()).willReturn(pretendXferTxn);
        given(
                        transferSemanticChecks.fullPureValidation(
                                pretendXferTxn.getCryptoTransfer().getTransfers(),
                                pretendXferTxn.getCryptoTransfer().getTokenTransfersList(),
                                validationProps))
                .willReturn(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN);

        // when:
        final var validity = subject.validateSemantics(accessor);

        // then:
        assertEquals(TRANSFERS_NOT_ZERO_SUM_FOR_TOKEN, validity);
    }

    @Test
    void hasCorrectApplicability() {
        givenValidTxnCtx(withAdjustments(a, -2L, b, 1L, c, 1L));

        // expect:
        assertTrue(subject.applicability().test(cryptoTransferTxn));
        assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
    }

    private void assertFailsWith(Runnable something, ResponseCodeEnum status) {
        var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    private void givenValidTxnCtx(TransferList wrapper) {
        cryptoTransferTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(ourTxnId())
                        .setCryptoTransfer(
                                CryptoTransferTransactionBody.newBuilder()
                                        .setTransfers(wrapper)
                                        .build())
                        .build();
    }

    private void givenValidTxnCtx() {
        cryptoTransferTxn = TransactionBody.newBuilder().setCryptoTransfer(xfers).build();
        given(txnCtx.accessor()).willReturn(accessor);
    }

    private TransactionID ourTxnId() {
        return TransactionID.newBuilder()
                .setAccountID(payer)
                .setTransactionValidStart(
                        Timestamp.newBuilder().setSeconds(Instant.now().getEpochSecond()))
                .build();
    }

    CryptoTransferTransactionBody xfers =
            CryptoTransferTransactionBody.newBuilder()
                    .setTransfers(
                            TransferList.newBuilder()
                                    .addAccountAmounts(adjustFrom(asAccount("0.0.75231"), -1_000))
                                    .addAccountAmounts(
                                            adjustFrom(
                                                    asAliasAccount(ByteString.copyFromUtf8("aaaa")),
                                                    +1_000))
                                    .build())
                    .addTokenTransfers(
                            TokenTransferList.newBuilder()
                                    .setToken(asToken("0.0.12345"))
                                    .addAllTransfers(
                                            List.of(
                                                    adjustFrom(asAccount("0.0.2"), -1_000),
                                                    adjustFrom(
                                                            asAliasAccount(
                                                                    ByteString.copyFromUtf8("bbb")),
                                                            +1_000))))
                    .build();
}
