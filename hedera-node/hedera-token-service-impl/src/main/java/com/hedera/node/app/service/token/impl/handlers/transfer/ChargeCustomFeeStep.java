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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.node.app.service.token.impl.util.TokenHandlerHelper.getIfUsable;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableNftStore;
import com.hedera.node.app.service.token.impl.WritableTokenRelationStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import java.util.Collections;
import java.util.Set;

public class ChargeCustomFeeStep implements TransferStep {
    private final CryptoTransferTransactionBody op;

    public ChargeCustomFeeStep(final CryptoTransferTransactionBody op) {
        this.op = op;
    }

    @Override
    public Set<Key> authorizingKeysIn(final TransferContext transferContext) {
        return Set.of();
    }

    @Override
    public void doIn(final TransferContext transferContext) {
        final var handleContext = transferContext.getHandleContext();
        final var tokenTransfers = op.tokenTransfersOrElse(Collections.emptyList());
        final var nftStore = handleContext.writableStore(WritableNftStore.class);
        final var accountStore = handleContext.writableStore(WritableAccountStore.class);
        final var tokenStore = handleContext.writableStore(WritableTokenStore.class);
        final var tokenRelStore = handleContext.writableStore(WritableTokenRelationStore.class);
        final var expiryValidator = handleContext.expiryValidator();

        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var token = getIfUsable(tokenId, tokenStore);
            final var customFees = token.customFeesOrElse(Collections.emptyList());
            if (customFees.isEmpty()) {
                continue;
            }
            for (final var aa : xfer.transfers()) {}
        }
        //        var change = changeManager.nextAssessableChange();
        //        while (change != null) {
        //            final var status = feeAssessor.assess(change, schedulesManager, changeManager, fees, props);
        //            if (status != OK) {
        //                return ImpliedTransfers.invalid(props, schedulesManager.metaUsed(), status);
        //            }
        //            change = changeManager.nextAssessableChange();
        //        }
        //
        //        return ImpliedTransfers.valid(
        //                props, changes, schedulesManager.metaUsed(), fees, resolvedAliases, numAutoCreations,
        // numLazyCreations);
    }

    //    public ResponseCodeEnum assess(
    //            BalanceChange change,
    //            CustomSchedulesManager customSchedulesManager,
    //            BalanceChangeManager changeManager,
    //            List<AssessedCustomFeeWrapper> accumulator,
    //            ImpliedTransfersMeta.ValidationProps props) {
    //        if (changeManager.getLevelNo() > props.maxNestedCustomFees()) {
    //            return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_RECURSION_DEPTH;
    //        }
    //        final var chargingToken = change.getToken();
    //
    //        final var feeMeta = customSchedulesManager.managedSchedulesFor(chargingToken);
    //        final var payer = change.getAccount();
    //        final var fees = feeMeta.customFees();
    //        /* Token treasuries are exempt from all custom fees */
    //        if (fees.isEmpty() || feeMeta.treasuryId().equals(payer)) {
    //            return OK;
    //        }
    //
    //        final var maxBalanceChanges = props.maxXferBalanceChanges();
    //        final var fixedFeeResult = assessFixedFees(feeMeta, payer, changeManager, accumulator, maxBalanceChanges);
    //        if (fixedFeeResult == ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED) {
    //            return CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
    //        }
    //
    //        /* A COMMON_FUNGIBLE token can have fractional fees but not royalty fees;
    //        and a NONFUNGIBLE_UNIQUE token can have royalty fees but not fractional fees.
    //        So these two if clauses are mutually exclusive. */
    //        if (fixedFeeResult == FRACTIONAL_FEE_ASSESSMENT_PENDING) {
    //            final var fractionalValidity =
    //                    fractionalFeeAssessor.assessAllFractional(change, feeMeta, changeManager, accumulator);
    //            if (fractionalValidity != OK) {
    //                return fractionalValidity;
    //            }
    //        } else if (fixedFeeResult == ROYALTY_FEE_ASSESSMENT_PENDING) {
    //            final var royaltyValidity =
    //                    royaltyFeeAssessor.assessAllRoyalties(change, feeMeta, changeManager, accumulator);
    //            if (royaltyValidity != OK) {
    //                return royaltyValidity;
    //            }
    //        }
    //
    //        return (changeManager.numChangesSoFar() > maxBalanceChanges)
    //                ? CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS
    //                : OK;
    //    }
    //
    //    private FixedFeeResult assessFixedFees(
    //            CustomFeeMeta feeMeta,
    //            Id payer,
    //            BalanceChangeManager balanceChangeManager,
    //            List<AssessedCustomFeeWrapper> accumulator,
    //            int maxBalanceChanges) {
    //        var result = ASSESSMENT_FINISHED;
    //        for (var fee : feeMeta.customFees()) {
    //            final var collector = fee.getFeeCollectorAsId();
    //            if (payer.equals(collector)) {
    //                continue;
    //            }
    //            if (fee.getFeeType() == FIXED_FEE) {
    //                // This is a top-level fixed fee, not a fallback royalty fee
    //                fixedFeeAssessor.assess(payer, feeMeta, fee, balanceChangeManager, accumulator,
    // IS_NOT_FALLBACK_FEE);
    //                if (balanceChangeManager.numChangesSoFar() > maxBalanceChanges) {
    //                    return ASSESSMENT_FAILED_WITH_TOO_MANY_ADJUSTMENTS_REQUIRED;
    //                }
    //            } else {
    //                if (fee.getFeeType() == FRACTIONAL_FEE) {
    //                    result = FRACTIONAL_FEE_ASSESSMENT_PENDING;
    //                } else {
    //                    result = ROYALTY_FEE_ASSESSMENT_PENDING;
    //                }
    //            }
    //        }
    //        return result;
    //    }
}
