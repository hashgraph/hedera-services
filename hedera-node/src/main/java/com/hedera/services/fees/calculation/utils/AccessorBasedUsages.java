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
package com.hedera.services.fees.calculation.utils;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.ConsensusOpsUsage;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hedera.services.usage.state.UsageAccumulator;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.util.UtilOpsUsage;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import java.util.EnumSet;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AccessorBasedUsages {
    private static final EnumSet<HederaFunctionality> supportedOps =
            EnumSet.of(
                    FileAppend,
                    CryptoTransfer,
                    CryptoCreate,
                    CryptoUpdate,
                    CryptoApproveAllowance,
                    CryptoDeleteAllowance,
                    ConsensusSubmitMessage,
                    TokenFeeScheduleUpdate,
                    TokenCreate,
                    TokenBurn,
                    TokenMint,
                    TokenAccountWipe,
                    TokenFreezeAccount,
                    TokenUnfreezeAccount,
                    TokenPause,
                    TokenUnpause,
                    UtilPrng);

    private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

    private final FileOpsUsage fileOpsUsage;
    private final TokenOpsUsage tokenOpsUsage;
    private final CryptoOpsUsage cryptoOpsUsage;
    private final ConsensusOpsUsage consensusOpsUsage;

    private final UtilOpsUsage utilOpsUsage;

    private final OpUsageCtxHelper opUsageCtxHelper;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public AccessorBasedUsages(
            FileOpsUsage fileOpsUsage,
            TokenOpsUsage tokenOpsUsage,
            CryptoOpsUsage cryptoOpsUsage,
            OpUsageCtxHelper opUsageCtxHelper,
            ConsensusOpsUsage consensusOpsUsage,
            UtilOpsUsage utilOpsUsage,
            GlobalDynamicProperties dynamicProperties) {
        this.fileOpsUsage = fileOpsUsage;
        this.tokenOpsUsage = tokenOpsUsage;
        this.cryptoOpsUsage = cryptoOpsUsage;
        this.opUsageCtxHelper = opUsageCtxHelper;
        this.consensusOpsUsage = consensusOpsUsage;
        this.utilOpsUsage = utilOpsUsage;
        this.dynamicProperties = dynamicProperties;
    }

    public void assess(SigUsage sigUsage, TxnAccessor accessor, UsageAccumulator into) {
        final var function = accessor.getFunction();
        if (!supportedOps.contains(function)) {
            throw new IllegalArgumentException(
                    "Usage estimation for " + function + " not yet migrated");
        }

        final var baseMeta = accessor.baseUsageMeta();
        if (function == CryptoTransfer) {
            estimateCryptoTransfer(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoCreate) {
            estimateCryptoCreate(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoUpdate) {
            estimateCryptoUpdate(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoApproveAllowance) {
            estimateCryptoApproveAllowance(sigUsage, accessor, baseMeta, into);
        } else if (function == CryptoDeleteAllowance) {
            estimateCryptoDeleteAllowance(sigUsage, accessor, baseMeta, into);
        } else if (function == ConsensusSubmitMessage) {
            estimateSubmitMessage(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenFeeScheduleUpdate) {
            estimateFeeScheduleUpdate(sigUsage, accessor, baseMeta, into);
        } else if (function == FileAppend) {
            estimateFileAppend(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenCreate) {
            estimateTokenCreate(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenBurn) {
            estimateTokenBurn(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenMint) {
            estimateTokenMint(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenFreezeAccount) {
            estimateTokenFreezeAccount(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenUnfreezeAccount) {
            estimateTokenUnfreezeAccount(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenAccountWipe) {
            estimateTokenWipe(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenPause) {
            estimateTokenPause(sigUsage, accessor, baseMeta, into);
        } else if (function == TokenUnpause) {
            estimateTokenUnpause(sigUsage, accessor, baseMeta, into);
        } else if (function == UtilPrng) {
            estimateUtilPrng(sigUsage, accessor, baseMeta, into);
        }
    }

    public boolean supports(HederaFunctionality function) {
        return supportedOps.contains(function);
    }

    private void estimateFeeScheduleUpdate(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var op = accessor.getTxn().getTokenFeeScheduleUpdate();
        final var opMeta = spanMapAccessor.getFeeScheduleUpdateMeta(accessor);
        final var usageCtx = opUsageCtxHelper.ctxForFeeScheduleUpdate(op);
        tokenOpsUsage.feeScheduleUpdateUsage(sigUsage, baseMeta, opMeta, usageCtx, into);
    }

    private void estimateFileAppend(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var opMeta = opUsageCtxHelper.metaForFileAppend(accessor.getTxn());
        fileOpsUsage.fileAppendUsage(sigUsage, opMeta, baseMeta, into);
    }

    private void estimateCryptoTransfer(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var xferMeta = accessor.availXferUsageMeta();
        xferMeta.setTokenMultiplier(dynamicProperties.feesTokenTransferUsageMultiplier());
        cryptoOpsUsage.cryptoTransferUsage(sigUsage, xferMeta, baseMeta, into);
    }

    private void estimateCryptoCreate(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var cryptoCreateMeta = accessor.getSpanMapAccessor().getCryptoCreateMeta(accessor);
        cryptoOpsUsage.cryptoCreateUsage(sigUsage, baseMeta, cryptoCreateMeta, into);
    }

    private void estimateCryptoUpdate(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var cryptoUpdateMeta = accessor.getSpanMapAccessor().getCryptoUpdateMeta(accessor);
        final var cryptoContext = opUsageCtxHelper.ctxForCryptoUpdate(accessor.getTxn());
        cryptoOpsUsage.cryptoUpdateUsage(sigUsage, baseMeta, cryptoUpdateMeta, cryptoContext, into);
    }

    private void estimateCryptoApproveAllowance(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var cryptoApproveMeta = accessor.getSpanMapAccessor().getCryptoApproveMeta(accessor);
        final var cryptoContext = opUsageCtxHelper.ctxForCryptoAllowance(accessor);
        cryptoOpsUsage.cryptoApproveAllowanceUsage(
                sigUsage, baseMeta, cryptoApproveMeta, cryptoContext, into);
    }

    private void estimateCryptoDeleteAllowance(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var cryptoDeleteAllowanceMeta =
                accessor.getSpanMapAccessor().getCryptoDeleteAllowanceMeta(accessor);
        cryptoOpsUsage.cryptoDeleteAllowanceUsage(
                sigUsage, baseMeta, cryptoDeleteAllowanceMeta, into);
    }

    private void estimateSubmitMessage(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var submitMeta = accessor.availSubmitUsageMeta();
        consensusOpsUsage.submitMessageUsage(sigUsage, submitMeta, baseMeta, into);
    }

    private void estimateTokenCreate(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenCreateMeta = accessor.getSpanMapAccessor().getTokenCreateMeta(accessor);
        tokenOpsUsage.tokenCreateUsage(sigUsage, baseMeta, tokenCreateMeta, into);
    }

    private void estimateTokenBurn(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenBurnMeta = accessor.getSpanMapAccessor().getTokenBurnMeta(accessor);
        tokenOpsUsage.tokenBurnUsage(sigUsage, baseMeta, tokenBurnMeta, into);
    }

    private void estimateTokenMint(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenMintMeta = opUsageCtxHelper.metaForTokenMint(accessor);
        tokenOpsUsage.tokenMintUsage(sigUsage, baseMeta, tokenMintMeta, into);
    }

    private void estimateTokenWipe(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenWipeMeta = accessor.getSpanMapAccessor().getTokenWipeMeta(accessor);
        tokenOpsUsage.tokenWipeUsage(sigUsage, baseMeta, tokenWipeMeta, into);
    }

    private void estimateTokenFreezeAccount(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenFreezeMeta = accessor.getSpanMapAccessor().getTokenFreezeMeta(accessor);
        tokenOpsUsage.tokenFreezeUsage(sigUsage, baseMeta, tokenFreezeMeta, into);
    }

    private void estimateTokenUnfreezeAccount(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenUnFreezeMeta = accessor.getSpanMapAccessor().getTokenUnfreezeMeta(accessor);
        tokenOpsUsage.tokenUnfreezeUsage(sigUsage, baseMeta, tokenUnFreezeMeta, into);
    }

    private void estimateTokenPause(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenPauseMeta = accessor.getSpanMapAccessor().getTokenPauseMeta(accessor);
        tokenOpsUsage.tokenPauseUsage(sigUsage, baseMeta, tokenPauseMeta, into);
    }

    private void estimateTokenUnpause(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var tokenUnpauseMeta = accessor.getSpanMapAccessor().getTokenUnpauseMeta(accessor);
        tokenOpsUsage.tokenUnpauseUsage(sigUsage, baseMeta, tokenUnpauseMeta, into);
    }

    private void estimateUtilPrng(
            SigUsage sigUsage,
            TxnAccessor accessor,
            BaseTransactionMeta baseMeta,
            UsageAccumulator into) {
        final var prngMeta = accessor.getSpanMapAccessor().getUtilPrngMeta(accessor);
        utilOpsUsage.prngUsage(sigUsage, baseMeta, prngMeta, into);
    }
}
