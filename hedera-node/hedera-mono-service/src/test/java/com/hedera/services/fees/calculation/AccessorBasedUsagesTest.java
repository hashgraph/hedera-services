/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation;

import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.services.state.submerkle.FcCustomFee.fractionalFee;
import static com.hedera.services.utils.accessors.SignedTxnAccessor.uncheckedFrom;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static java.util.stream.Collectors.toList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.utils.AccessorBasedUsages;
import com.hedera.services.fees.calculation.utils.OpUsageCtxHelper;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.crypto.CryptoTransferMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.crypto.ExtantCryptoContext;
import com.hedera.services.usage.file.FileAppendMeta;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.ExtantFeeScheduleContext;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.token.meta.TokenBurnMeta;
import com.hedera.services.usage.token.meta.TokenCreateMeta;
import com.hedera.services.usage.token.meta.TokenFreezeMeta;
import com.hedera.services.usage.token.meta.TokenMintMeta;
import com.hedera.services.usage.token.meta.TokenPauseMeta;
import com.hedera.services.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.services.usage.token.meta.TokenUnpauseMeta;
import com.hedera.services.usage.token.meta.TokenWipeMeta;
import com.hedera.services.usage.util.UtilOpsUsage;
import com.hedera.services.usage.util.UtilPrngMeta;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFeeScheduleUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccessorBasedUsagesTest {
    private final String memo = "Even the most cursory inspection would yield that...";
    private final long now = 1_234_567L;
    private final SigUsage sigUsage = new SigUsage(1, 2, 3);
    private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private SignedTxnAccessor txnAccessor;

    @Mock private OpUsageCtxHelper opUsageCtxHelper;
    @Mock private FileOpsUsage fileOpsUsage;
    @Mock private TokenOpsUsage tokenOpsUsage;
    @Mock private CryptoOpsUsage cryptoOpsUsage;
    @Mock private ConsensusOpsUsage consensusOpsUsage;
    @Mock private UtilOpsUsage utilOpsUsage;
    @Mock private GlobalDynamicProperties dynamicProperties;

    private AccessorBasedUsages subject;

    @BeforeEach
    void setUp() {
        subject =
                new AccessorBasedUsages(
                        fileOpsUsage,
                        tokenOpsUsage,
                        cryptoOpsUsage,
                        opUsageCtxHelper,
                        consensusOpsUsage,
                        utilOpsUsage,
                        dynamicProperties);
    }

    @Test
    void throwsIfNotSupported() {
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(ContractCreate);

        assertThrows(
                IllegalArgumentException.class,
                () -> subject.assess(sigUsage, txnAccessor, accumulator));
    }

    @Test
    void worksAsExpectedForFileAppend() {
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var opMeta = new FileAppendMeta(1_234, 1_234_567L);
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(FileAppend);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(opUsageCtxHelper.metaForFileAppend(TransactionBody.getDefaultInstance()))
                .willReturn(opMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(fileOpsUsage).fileAppendUsage(sigUsage, opMeta, baseMeta, accumulator);
    }

    @Test
    void worksAsExpectedForCryptoTransfer() {
        int multiplier = 30;
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var xferMeta = new CryptoTransferMeta(1, 3, 7, 4);
        final var usageAccumulator = new UsageAccumulator();

        given(dynamicProperties.feesTokenTransferUsageMultiplier()).willReturn(multiplier);
        given(txnAccessor.getFunction()).willReturn(CryptoTransfer);
        given(txnAccessor.availXferUsageMeta()).willReturn(xferMeta);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);

        subject.assess(sigUsage, txnAccessor, usageAccumulator);

        verify(cryptoOpsUsage).cryptoTransferUsage(sigUsage, xferMeta, baseMeta, usageAccumulator);
        assertEquals(multiplier, xferMeta.getTokenMultiplier());
    }

    @Test
    void worksAsExpectedForSubmitMessage() {
        final var baseMeta = new BaseTransactionMeta(100, 0);
        final var submitMeta = new SubmitMessageMeta(1_234);
        final var usageAccumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(ConsensusSubmitMessage);
        given(txnAccessor.availSubmitUsageMeta()).willReturn(submitMeta);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);

        subject.assess(sigUsage, txnAccessor, usageAccumulator);

        verify(consensusOpsUsage)
                .submitMessageUsage(sigUsage, submitMeta, baseMeta, usageAccumulator);
    }

    @Test
    void worksAsExpectedForFeeScheduleUpdate() {
        final var realAccessor = uncheckedFrom(signedFeeScheduleUpdateTxn());

        final var op = feeScheduleUpdateTxn().getTokenFeeScheduleUpdate();
        final var opMeta = new FeeScheduleUpdateMeta(now, 234);
        final var baseMeta = new BaseTransactionMeta(memo.length(), 0);
        final var feeScheduleCtx = new ExtantFeeScheduleContext(now, 123);

        given(opUsageCtxHelper.ctxForFeeScheduleUpdate(op)).willReturn(feeScheduleCtx);
        spanMapAccessor.setFeeScheduleUpdateMeta(realAccessor, opMeta);

        final var accum = new UsageAccumulator();
        subject.assess(sigUsage, realAccessor, accum);

        verify(tokenOpsUsage)
                .feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, feeScheduleCtx, accum);
    }

    @Test
    void worksAsExpectedForTokenCreate() {
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var opMeta =
                new TokenCreateMeta.Builder()
                        .baseSize(1_234)
                        .customFeeScheleSize(0)
                        .lifeTime(1_234_567L)
                        .fungibleNumTransfers(0)
                        .nftsTranfers(0)
                        .nftsTranfers(1000)
                        .nftsTranfers(1)
                        .build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(TokenCreate);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getTokenCreateMeta(any())).willReturn(opMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenCreateUsage(sigUsage, baseMeta, opMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenBurn() {
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var tokenBurnMeta = new TokenBurnMeta(1000, SubType.TOKEN_FUNGIBLE_COMMON, 2345L, 2);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenBurn);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenBurnMeta(any())).willReturn(tokenBurnMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenBurnUsage(sigUsage, baseMeta, tokenBurnMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenWipe() {
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var tokenWipeMeta =
                new TokenWipeMeta(1000, SubType.TOKEN_NON_FUNGIBLE_UNIQUE, 2345L, 2);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenAccountWipe);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenWipeMeta(any())).willReturn(tokenWipeMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenWipeUsage(sigUsage, baseMeta, tokenWipeMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenMint() {
        final var baseMeta = new BaseTransactionMeta(100, 2);
        final var tokenMintMeta =
                new TokenMintMeta(1000, SubType.TOKEN_NON_FUNGIBLE_UNIQUE, 2345L, 20000);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenMint);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(opUsageCtxHelper.metaForTokenMint(txnAccessor)).willReturn(tokenMintMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenMintUsage(sigUsage, baseMeta, tokenMintMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenFreezeAccount() {
        final var baseMeta = new BaseTransactionMeta(0, 0);
        final var tokenFreezeMeta = new TokenFreezeMeta(48);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenFreezeAccount);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenFreezeMeta(any()))
                .willReturn(tokenFreezeMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenFreezeUsage(sigUsage, baseMeta, tokenFreezeMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenUnfreezeAccount() {
        final var baseMeta = new BaseTransactionMeta(0, 0);
        final var tokenUnfreezeMeta = new TokenUnfreezeMeta(48);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenUnfreezeAccount);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenUnfreezeMeta(any()))
                .willReturn(tokenUnfreezeMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage)
                .tokenUnfreezeUsage(sigUsage, baseMeta, tokenUnfreezeMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenPause() {
        final var baseMeta = new BaseTransactionMeta(0, 0);
        final var tokenPauseMeta = new TokenPauseMeta(24);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenPause);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenPauseMeta(any())).willReturn(tokenPauseMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenPauseUsage(sigUsage, baseMeta, tokenPauseMeta, accumulator);
    }

    @Test
    void worksAsExpectedForTokenUnpause() {
        final var baseMeta = new BaseTransactionMeta(0, 0);
        final var tokenUnpauseMeta = new TokenUnpauseMeta(24);
        final var accumulator = new UsageAccumulator();
        given(txnAccessor.getFunction()).willReturn(TokenUnpause);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getSpanMapAccessor().getTokenUnpauseMeta(any()))
                .willReturn(tokenUnpauseMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(tokenOpsUsage).tokenUnpauseUsage(sigUsage, baseMeta, tokenUnpauseMeta, accumulator);
    }

    @Test
    void worksAsExpectedForCryptoCreate() {
        final var baseMeta = new BaseTransactionMeta(100, 0);
        final var opMeta =
                new CryptoCreateMeta.Builder()
                        .baseSize(1_234)
                        .lifeTime(1_234_567L)
                        .maxAutomaticAssociations(3)
                        .build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(CryptoCreate);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getCryptoCreateMeta(any())).willReturn(opMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(cryptoOpsUsage).cryptoCreateUsage(sigUsage, baseMeta, opMeta, accumulator);
    }

    @Test
    void worksAsExpectedForCryptoUpdate() {
        final var baseMeta = new BaseTransactionMeta(100, 0);
        final var opMeta =
                new CryptoUpdateMeta.Builder()
                        .keyBytesUsed(123)
                        .msgBytesUsed(1_234)
                        .memoSize(100)
                        .effectiveNow(now)
                        .expiry(1_234_567L)
                        .hasProxy(false)
                        .maxAutomaticAssociations(3)
                        .hasMaxAutomaticAssociations(true)
                        .build();
        final var cryptoContext =
                ExtantCryptoContext.newBuilder()
                        .setCurrentKey(Key.getDefaultInstance())
                        .setCurrentMemo(memo)
                        .setCurrentExpiry(now)
                        .setCurrentlyHasProxy(false)
                        .setCurrentNumTokenRels(0)
                        .setCurrentMaxAutomaticAssociations(0)
                        .setCurrentCryptoAllowances(Collections.emptyMap())
                        .setCurrentTokenAllowances(Collections.emptyMap())
                        .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                        .build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(CryptoUpdate);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getCryptoUpdateMeta(any())).willReturn(opMeta);
        given(opUsageCtxHelper.ctxForCryptoUpdate(any())).willReturn(cryptoContext);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(cryptoOpsUsage)
                .cryptoUpdateUsage(sigUsage, baseMeta, opMeta, cryptoContext, accumulator);
    }

    @Test
    void worksAsExpectedForCryptoApprove() {
        final var baseMeta = new BaseTransactionMeta(100, 0);
        final var opMeta =
                CryptoApproveAllowanceMeta.newBuilder()
                        .effectiveNow(Instant.now().getEpochSecond())
                        .build();
        final var cryptoContext =
                ExtantCryptoContext.newBuilder()
                        .setCurrentKey(Key.getDefaultInstance())
                        .setCurrentMemo(memo)
                        .setCurrentExpiry(now)
                        .setCurrentlyHasProxy(false)
                        .setCurrentNumTokenRels(0)
                        .setCurrentMaxAutomaticAssociations(0)
                        .setCurrentCryptoAllowances(Collections.emptyMap())
                        .setCurrentTokenAllowances(Collections.emptyMap())
                        .setCurrentApproveForAllNftAllowances(Collections.emptySet())
                        .build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(CryptoApproveAllowance);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getCryptoApproveMeta(any())).willReturn(opMeta);
        given(opUsageCtxHelper.ctxForCryptoAllowance(txnAccessor)).willReturn(cryptoContext);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(cryptoOpsUsage)
                .cryptoApproveAllowanceUsage(
                        sigUsage, baseMeta, opMeta, cryptoContext, accumulator);
    }

    @Test
    void worksAsExpectedForCryptoDeleteAllowance() {
        final var baseMeta = new BaseTransactionMeta(100, 0);
        final var opMeta =
                CryptoDeleteAllowanceMeta.newBuilder()
                        .msgBytesUsed(112)
                        .effectiveNow(Instant.now().getEpochSecond())
                        .build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(CryptoDeleteAllowance);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getCryptoDeleteAllowanceMeta(any()))
                .willReturn(opMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(cryptoOpsUsage).cryptoDeleteAllowanceUsage(sigUsage, baseMeta, opMeta, accumulator);
    }

    @Test
    void supportsIfInSet() {
        assertTrue(subject.supports(CryptoTransfer));
        assertTrue(subject.supports(ConsensusSubmitMessage));
        assertTrue(subject.supports(CryptoCreate));
        assertTrue(subject.supports(CryptoUpdate));
        assertTrue(subject.supports(UtilPrng));
        assertFalse(subject.supports(ContractCreate));
    }

    @Test
    void worksAsExpectedForPrng() {
        final var baseMeta = new BaseTransactionMeta(0, 0);
        final var opMeta = UtilPrngMeta.newBuilder().msgBytesUsed(32).build();
        final var accumulator = new UsageAccumulator();

        given(txnAccessor.getFunction()).willReturn(UtilPrng);
        given(txnAccessor.baseUsageMeta()).willReturn(baseMeta);
        given(txnAccessor.getTxn()).willReturn(TransactionBody.getDefaultInstance());
        given(txnAccessor.getSpanMapAccessor().getUtilPrngMeta(any())).willReturn(opMeta);

        subject.assess(sigUsage, txnAccessor, accumulator);

        verify(utilOpsUsage).prngUsage(sigUsage, baseMeta, opMeta, accumulator);
    }

    private Transaction signedFeeScheduleUpdateTxn() {
        return Transaction.newBuilder()
                .setSignedTransactionBytes(
                        SignedTransaction.newBuilder()
                                .setBodyBytes(feeScheduleUpdateTxn().toByteString())
                                .build()
                                .toByteString())
                .build();
    }

    private TransactionBody feeScheduleUpdateTxn() {
        return TransactionBody.newBuilder()
                .setMemo(memo)
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(Timestamp.newBuilder().setSeconds(now)))
                .setTokenFeeScheduleUpdate(
                        TokenFeeScheduleUpdateTransactionBody.newBuilder().addAllCustomFees(fees()))
                .build();
    }

    private List<CustomFee> fees() {
        final var collector = new EntityId(1, 2, 3);
        final var aDenom = new EntityId(2, 3, 4);
        final var bDenom = new EntityId(3, 4, 5);

        return List.of(
                        fixedFee(1, null, collector, false),
                        fixedFee(2, aDenom, collector, false),
                        fixedFee(2, bDenom, collector, false),
                        fractionalFee(1, 2, 1, 2, false, collector, false),
                        fractionalFee(1, 3, 1, 2, false, collector, false),
                        fractionalFee(1, 4, 1, 2, false, collector, false))
                .stream()
                .map(FcCustomFee::asGrpc)
                .collect(toList());
    }
}
