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
package com.hedera.services.txns.submission;

import static com.hedera.services.txns.submission.PresolvencyFlaws.WELL_KNOWN_FLAWS;
import static com.hedera.services.txns.submission.PresolvencyFlaws.responseForFlawed;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_NOT_ACTIVE;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.EnumSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Implements the appropriate stages of precheck for a transaction to be submitted to the network,
 * either a top-level transaction or a {@code CryptoTransfer} query payment.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public final class TransactionPrecheck {
    private final QueryFeeCheck queryFeeCheck;
    private final StagedPrechecks stagedPrechecks;
    private final CurrentPlatformStatus currentPlatformStatus;

    private static final Set<Characteristic> TOP_LEVEL_CHARACTERISTICS =
            EnumSet.of(Characteristic.MUST_PASS_SYSTEM_SCREEN);
    private static final Set<Characteristic> QUERY_PAYMENT_CHARACTERISTICS =
            EnumSet.of(
                    Characteristic.MUST_BE_CRYPTO_TRANSFER,
                    Characteristic.MUST_BE_SOLVENT_FOR_SVC_FEES);

    @Inject
    public TransactionPrecheck(
            final QueryFeeCheck queryFeeCheck,
            final StagedPrechecks stagedPrechecks,
            final CurrentPlatformStatus currentPlatformStatus) {
        this.queryFeeCheck = queryFeeCheck;
        this.stagedPrechecks = stagedPrechecks;
        this.currentPlatformStatus = currentPlatformStatus;
    }

    public Pair<TxnValidityAndFeeReq, SignedTxnAccessor> performForTopLevel(
            final Transaction signedTxn) {
        return performance(signedTxn, TOP_LEVEL_CHARACTERISTICS);
    }

    public Pair<TxnValidityAndFeeReq, SignedTxnAccessor> performForQueryPayment(
            final Transaction signedTxn) {
        final var prelim = performance(signedTxn, QUERY_PAYMENT_CHARACTERISTICS);
        final var accessor = prelim.getRight();
        if (null == accessor) {
            return prelim;
        }

        final var xferTxn = accessor.getTxn();
        final var xfersStatus = queryFeeCheck.validateQueryPaymentTransfers(xferTxn);
        if (xfersStatus != OK) {
            return failureFor(
                    new TxnValidityAndFeeReq(xfersStatus, prelim.getLeft().getRequiredFee()));
        }
        return prelim;
    }

    private Pair<TxnValidityAndFeeReq, SignedTxnAccessor> performance(
            final Transaction signedTxn, final Set<Characteristic> characteristics) {
        if (currentPlatformStatus.get() != ACTIVE) {
            return WELL_KNOWN_FLAWS.get(PLATFORM_NOT_ACTIVE);
        }

        final var structuralAssessment = stagedPrechecks.assessStructure(signedTxn);
        final var accessor = structuralAssessment.getRight();
        if (null == accessor) {
            return structuralAssessment;
        }

        final var txn = accessor.getTxn();

        final var syntaxStatus = stagedPrechecks.validateSyntax(txn);
        if (syntaxStatus != OK) {
            return responseForFlawed(syntaxStatus);
        }

        final var semanticStatus = checkSemantics(accessor, characteristics);
        if (semanticStatus != OK) {
            return responseForFlawed(semanticStatus);
        }

        final var solvencyStatus =
                characteristics.contains(Characteristic.MUST_BE_SOLVENT_FOR_SVC_FEES)
                        ? stagedPrechecks.assessSolvencyWithSvcFees(accessor)
                        : stagedPrechecks.assessSolvencySansSvcFees(accessor);
        if (solvencyStatus.getValidity() != OK) {
            return failureFor(solvencyStatus);
        }

        if (characteristics.contains(Characteristic.MUST_PASS_SYSTEM_SCREEN)) {
            final var systemStatus = stagedPrechecks.systemScreen(accessor);
            if (systemStatus != OK) {
                return failureFor(
                        new TxnValidityAndFeeReq(systemStatus, solvencyStatus.getRequiredFee()));
            }
        }

        return Pair.of(solvencyStatus, accessor);
    }

    private Pair<TxnValidityAndFeeReq, SignedTxnAccessor> failureFor(
            final TxnValidityAndFeeReq feeReqStatus) {
        return Pair.of(feeReqStatus, null);
    }

    private ResponseCodeEnum checkSemantics(
            final TxnAccessor accessor, final Set<Characteristic> characteristics) {
        return characteristics.contains(Characteristic.MUST_BE_CRYPTO_TRANSFER)
                ? stagedPrechecks.validateSemantics(accessor, CryptoTransfer, INSUFFICIENT_TX_FEE)
                : stagedPrechecks.validateSemantics(
                        accessor, accessor.getFunction(), NOT_SUPPORTED);
    }

    private enum Characteristic {
        MUST_BE_CRYPTO_TRANSFER,
        MUST_PASS_SYSTEM_SCREEN,
        MUST_BE_SOLVENT_FOR_SVC_FEES
    }
}
