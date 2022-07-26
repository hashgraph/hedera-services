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

import static com.hedera.services.txns.validation.PureValidation.queryableAccountStatus;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.KEY_PREFIX_MISMATCH;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PAYER_ACCOUNT_NOT_FOUND;

import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.FeeExemptions;
import com.hedera.services.legacy.exception.InvalidAccountIDException;
import com.hedera.services.legacy.exception.KeyPrefixMismatchException;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.fee.FeeObject;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Determines if the payer account set in the {@code TransactionID} is expected to be both willing
 * and able to pay the transaction fees.
 *
 * <p>For more details, please see
 * https://github.com/hashgraph/hedera-services/blob/master/docs/transaction-prechecks.md
 */
@Singleton
public class SolvencyPrecheck {
    private static final Logger log = LogManager.getLogger(SolvencyPrecheck.class);

    private static final TxnValidityAndFeeReq VERIFIED_EXEMPT = new TxnValidityAndFeeReq(OK);
    private static final TxnValidityAndFeeReq LOST_PAYER_EXPIRATION_RACE =
            new TxnValidityAndFeeReq(FAIL_FEE);

    private final FeeExemptions feeExemptions;
    private final FeeCalculator feeCalculator;
    private final OptionValidator validator;
    private final PrecheckVerifier precheckVerifier;
    private final Supplier<StateView> stateView;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;

    @Inject
    public SolvencyPrecheck(
            FeeExemptions feeExemptions,
            FeeCalculator feeCalculator,
            OptionValidator validator,
            PrecheckVerifier precheckVerifier,
            Supplier<StateView> stateView,
            Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts) {
        this.accounts = accounts;
        this.validator = validator;
        this.stateView = stateView;
        this.feeExemptions = feeExemptions;
        this.feeCalculator = feeCalculator;
        this.precheckVerifier = precheckVerifier;
    }

    TxnValidityAndFeeReq assessSansSvcFees(SignedTxnAccessor accessor) {
        return assess(accessor, false);
    }

    TxnValidityAndFeeReq assessWithSvcFees(SignedTxnAccessor accessor) {
        return assess(accessor, true);
    }

    private TxnValidityAndFeeReq assess(SignedTxnAccessor accessor, boolean includeSvcFee) {
        final var payerStatus =
                queryableAccountStatus(
                        EntityNum.fromAccountId(accessor.getPayer()), accounts.get());
        if (payerStatus != OK) {
            return new TxnValidityAndFeeReq(PAYER_ACCOUNT_NOT_FOUND);
        }

        final var sigsStatus = checkSigs(accessor);
        if (sigsStatus != OK) {
            return new TxnValidityAndFeeReq(sigsStatus);
        }

        if (feeExemptions.hasExemptPayer(accessor)) {
            return VERIFIED_EXEMPT;
        }

        return solvencyOfVerifiedPayer(accessor, includeSvcFee);
    }

    private TxnValidityAndFeeReq solvencyOfVerifiedPayer(
            SignedTxnAccessor accessor, boolean includeSvcFee) {
        final var payerId = EntityNum.fromAccountId(accessor.getPayer());
        final var payerAccount = accounts.get().get(payerId);

        try {
            final var now = accessor.getTxnId().getTransactionValidStart();
            final var payerKey = payerAccount.getAccountKey();
            final var estimatedFees =
                    feeCalculator.estimateFee(accessor, payerKey, stateView.get(), now);
            final var estimatedReqFee = totalOf(estimatedFees, includeSvcFee);

            if (accessor.getTxn().getTransactionFee() < estimatedReqFee) {
                return new TxnValidityAndFeeReq(INSUFFICIENT_TX_FEE, estimatedReqFee);
            }

            final var estimatedAdj =
                    Math.min(0L, feeCalculator.estimatedNonFeePayerAdjustments(accessor, now));
            final var requiredPayerBalance = estimatedReqFee - estimatedAdj;
            final var payerBalance = payerAccount.getBalance();
            var finalStatus = OK;
            if (payerBalance < requiredPayerBalance) {
                final var expiryStatus =
                        validator.expiryStatusGiven(
                                payerBalance,
                                payerAccount.getExpiry(),
                                payerAccount.isSmartContract());
                finalStatus = expiryStatus != OK ? expiryStatus : INSUFFICIENT_PAYER_BALANCE;
            }

            return new TxnValidityAndFeeReq(finalStatus, estimatedReqFee);
        } catch (Exception suspicious) {
            log.warn(
                    "Fee calculation failure may be justifiable due to an expiring payer, but...",
                    suspicious);
            return LOST_PAYER_EXPIRATION_RACE;
        }
    }

    private long totalOf(FeeObject fees, boolean includeSvcFee) {
        return (includeSvcFee ? fees.getServiceFee() : 0)
                + fees.getNodeFee()
                + fees.getNetworkFee();
    }

    private ResponseCodeEnum checkSigs(SignedTxnAccessor accessor) {
        try {
            return precheckVerifier.hasNecessarySignatures(accessor) ? OK : INVALID_SIGNATURE;
        } catch (KeyPrefixMismatchException ignore) {
            return KEY_PREFIX_MISMATCH;
        } catch (InvalidAccountIDException ignore) {
            return INVALID_ACCOUNT_ID;
        } catch (Exception ignore) {
            return INVALID_SIGNATURE;
        }
    }
}
