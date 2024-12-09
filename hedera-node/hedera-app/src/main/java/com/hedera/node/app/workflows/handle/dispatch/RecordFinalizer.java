/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.dispatch;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.CHILD;
import static com.hedera.node.app.spi.workflows.HandleContext.TransactionCategory.SCHEDULED;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.impl.handlers.FinalizeRecordHandler;
import com.hedera.node.app.spi.workflows.record.StreamBuilder;
import com.hedera.node.app.workflows.handle.Dispatch;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Finalizes the record based on the transaction category. The record
 * finalization is delegated to the parent or child record finalizer.
 */
@Singleton
public class RecordFinalizer {
    private final FinalizeRecordHandler recordFinalizer;

    /**
     * Creates a record finalizer with the given dependencies.
     *
     * @param recordFinalizer       the parent record finalizer
     */
    @Inject
    public RecordFinalizer(final FinalizeRecordHandler recordFinalizer) {
        this.recordFinalizer = recordFinalizer;
    }

    /**
     * Finalizes the record based on the transaction category. The record finalization is delegated to the
     * parent or child record finalizer. The parent record finalizer is used for user and scheduled transactions
     * and the child record finalizer is used for child and preceding transactions.
     * @param dispatch the dispatch
     */
    public void finalizeRecord(@NonNull final Dispatch dispatch) {
        requireNonNull(dispatch);
        final var category = dispatch.txnCategory();
        // We only paid staking rewards for execute_immediate=true scheduled transactions triggered by a user
        // ScheduleCreate or ScheduleSign for mono-service compatibility; it would be even more complicated
        // to do this when a contract's child dispatch is triggering the execute_immediate=true schedule, and
        // no mono-service precedent forces us to do it---so we don't
        final var effectiveCategory =
                category != SCHEDULED ? category : (dispatch.stack().scheduledParentIsUser() ? SCHEDULED : CHILD);
        switch (effectiveCategory) {
            case USER, SCHEDULED -> recordFinalizer.finalizeStakingRecord(
                    dispatch.finalizeContext(),
                    dispatch.txnInfo().functionality(),
                    extraRewardReceivers(
                            dispatch.txnInfo().txBody(), dispatch.txnInfo().functionality(), dispatch.recordBuilder()),
                    dispatch.handleContext().dispatchPaidRewards());
            case CHILD, PRECEDING -> recordFinalizer.finalizeNonStakingRecord(
                    dispatch.finalizeContext(), dispatch.txnInfo().functionality());
        }
    }

    /**
     * Returns a set of "extra" account ids that should be considered as eligible for
     * collecting their accrued staking rewards with the given transaction info and
     * record builder.
     *
     * <p><b>IMPORTANT:</b> Needed only for mono-service fidelity.
     *
     * <p>There are three cases, none of which HIP-406 defined as a reward situation;
     * but were "false positives" in the original mono-service implementation:
     * <ol>
     *     <li>For a crypto transfer, any account explicitly listed in the HBAR
     *     transfer list, even with a zero balance adjustment.</li>
     *     <li>For a contract operation, any called contract.</li>
     *     <li>For a contract operation, any account loaded in a child
     *     transaction (primarily, any account involved in a child
     *     token transfer).</li>
     * </ol>
     *
     * @param body the {@link TransactionBody} of the transaction
     * @param function the {@link HederaFunctionality} of the transaction
     * @param recordBuilder the record builder
     * @return the set of extra account ids
     */
    public Set<AccountID> extraRewardReceivers(
            @Nullable final TransactionBody body,
            @NonNull final HederaFunctionality function,
            @NonNull final StreamBuilder recordBuilder) {
        if (body == null || recordBuilder.status() != SUCCESS) {
            return emptySet();
        }
        return switch (function) {
            case CRYPTO_TRANSFER -> zeroAdjustIdsFrom(body.cryptoTransferOrThrow()
                    .transfersOrElse(TransferList.DEFAULT)
                    .accountAmounts());
            case ETHEREUM_TRANSACTION, CONTRACT_CALL, CONTRACT_CREATE -> recordBuilder.explicitRewardSituationIds();
            default -> emptySet();
        };
    }

    /**
     * Returns any ids from the given list of explicit hbar adjustments that have a zero amount.
     *
     * @param explicitHbarAdjustments the list of explicit hbar adjustments
     * @return the set of account ids that have a zero amount
     */
    @NonNull
    Set<AccountID> zeroAdjustIdsFrom(@NonNull final List<AccountAmount> explicitHbarAdjustments) {
        return explicitHbarAdjustments.stream()
                .filter(aa -> aa.amount() == 0)
                .map(AccountAmount::accountID)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
