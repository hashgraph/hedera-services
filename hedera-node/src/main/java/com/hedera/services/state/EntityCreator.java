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
package com.hedera.services.state;

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.records.RecordCache;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

public interface EntityCreator {
    String EMPTY_MEMO = "";
    List<FcAssessedCustomFee> NO_CUSTOM_FEES = Collections.emptyList();

    /**
     * Sets the ledger for the entity creator.
     *
     * @param ledger the ledger to use
     */
    void setLedger(HederaLedger ledger);

    /**
     * Sets needed properties like expiry and submitting member to {@link ExpirableTxnRecord} and
     * adds record to state based on {@code GlobalDynamicProperties.cacheRecordsTtl}. If not it is
     * added to be tracked for Expiry to {@link RecordCache}
     *
     * @param id account id
     * @param expiringRecord expirable transaction record
     * @param consensusTime consensus timestamp
     * @param submittingMember submitting member
     * @return the {@link ExpirableTxnRecord} after setting needed properties
     */
    ExpirableTxnRecord saveExpiringRecord(
            AccountID id,
            ExpirableTxnRecord expiringRecord,
            long consensusTime,
            long submittingMember);

    /**
     * Returns a {@link ExpirableTxnRecord.Builder} summarizing the information for the given
     * top-level transaction.
     *
     * @param fee the fee to include the record
     * @param hash the hash of the transaction to include in the record
     * @param accessor the accessor for the id, memo, and schedule reference (if any) for the
     *     transaction
     * @param consensusTime the consensus time of the transaction
     * @param receiptBuilder the in-progress builder for the receipt for the record
     * @param assessedCustomFees the custom fees assessed during the transaction
     * @param sideEffectsTracker the side effects tracked throughout the transaction
     * @return a {@link ExpirableTxnRecord.Builder} summarizing the input
     */
    ExpirableTxnRecord.Builder createTopLevelRecord(
            long fee,
            byte[] hash,
            TxnAccessor accessor,
            Instant consensusTime,
            TxnReceipt.Builder receiptBuilder,
            List<FcAssessedCustomFee> assessedCustomFees,
            SideEffectsTracker sideEffectsTracker);

    /**
     * Returns a {@link ExpirableTxnRecord.Builder} summarizing the information for the given
     * synthetic transaction.
     *
     * @param assessedCustomFees the custom fees assessed during the transaction
     * @param sideEffectsTracker the side effects tracked throughout the transaction
     * @param memo memo to be used for transaction
     * @return a {@link ExpirableTxnRecord.Builder} summarizing the input
     */
    ExpirableTxnRecord.Builder createSuccessfulSyntheticRecord(
            List<FcAssessedCustomFee> assessedCustomFees,
            SideEffectsTracker sideEffectsTracker,
            String memo);

    /**
     * Returns a {@link ExpirableTxnRecord.Builder} summarizing a failed synthetic transaction.
     *
     * @param failureReason the cause of the failure
     * @return a {@link ExpirableTxnRecord.Builder} summarizing the input
     */
    ExpirableTxnRecord.Builder createUnsuccessfulSyntheticRecord(ResponseCodeEnum failureReason);

    /**
     * Returns a {@link ExpirableTxnRecord.Builder} for a transaction that failed due to an internal
     * error.
     *
     * @param accessor transaction accessor
     * @param consensusTimestamp consensus timestamp
     * @return a record of a invalid failure transaction
     */
    ExpirableTxnRecord.Builder createInvalidFailureRecord(
            TxnAccessor accessor, Instant consensusTimestamp);
}
