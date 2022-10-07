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
package com.hedera.services.records;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.FAIL_INVALID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.google.common.cache.Cache;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionReceipt;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RecordCache {
    private static final TxnReceipt UNKNOWN_RECEIPT =
            TxnReceipt.newBuilder().setStatus(UNKNOWN.name()).build();

    static final Boolean MARKER = Boolean.TRUE;

    private EntityCreator creator;
    private Cache<TransactionID, Boolean> timedReceiptCache;
    private Map<TransactionID, TxnIdRecentHistory> histories;

    @Inject
    public RecordCache(
            Cache<TransactionID, Boolean> cache, Map<TransactionID, TxnIdRecentHistory> histories) {
        this.histories = histories;
        this.timedReceiptCache = cache;
    }

    @Inject
    void setCreator(final EntityCreator creator) {
        this.creator = creator;
    }

    public void addPreConsensus(final TransactionID txnId) {
        timedReceiptCache.put(txnId, Boolean.TRUE);
    }

    void setPostConsensus(
            final TransactionID txnId,
            final ResponseCodeEnum status,
            final ExpirableTxnRecord expirableTxnRecord) {
        final var recentHistory =
                histories.computeIfAbsent(txnId, ignore -> new TxnIdRecentHistory());
        recentHistory.observe(expirableTxnRecord, status);
    }

    public void setFailInvalid(
            final AccountID effectivePayer,
            final TxnAccessor accessor,
            final Instant consensusTimestamp,
            final long submittingMember) {
        final var recordBuilder = creator.createInvalidFailureRecord(accessor, consensusTimestamp);
        final var expiringRecord =
                creator.saveExpiringRecord(
                        effectivePayer,
                        recordBuilder.build(),
                        consensusTimestamp.getEpochSecond(),
                        submittingMember);

        final var recentHistory =
                histories.computeIfAbsent(accessor.getTxnId(), ignore -> new TxnIdRecentHistory());
        recentHistory.observe(expiringRecord, FAIL_INVALID);
    }

    public boolean isReceiptPresent(final TransactionID txnId) {
        return histories.containsKey(txnId) || timedReceiptCache.getIfPresent(txnId) == MARKER;
    }

    public TxnReceipt getPriorityReceipt(final TransactionID txnId) {
        final var recentHistory = histories.get(txnId);
        if (recentHistory != null) {
            return receiptFrom(recentHistory);
        }
        return timedReceiptCache.getIfPresent(txnId) == MARKER ? UNKNOWN_RECEIPT : null;
    }

    public List<TransactionRecord> getDuplicateRecords(final TransactionID txnId) {
        return duplicatesOf(txnId);
    }

    public List<TransactionReceipt> getDuplicateReceipts(final TransactionID txnId) {
        return duplicatesOf(txnId).stream().map(TransactionRecord::getReceipt).toList();
    }

    public List<TransactionReceipt> getChildReceipts(final TransactionID txnId) {
        return transformedChildrenOf(txnId, childRecord -> childRecord.getReceipt().toGrpc());
    }

    public List<TransactionRecord> getChildRecords(final TransactionID txnId) {
        return transformedChildrenOf(txnId, ExpirableTxnRecord::asGrpc);
    }

    private <T> List<T> transformedChildrenOf(
            final TransactionID txnId, final Function<ExpirableTxnRecord, T> transform) {
        final var priorityRecord = getPriorityRecord(txnId);
        if (priorityRecord == null) {
            return Collections.emptyList();
        } else {
            final var numChildren = priorityRecord.getNumChildRecords();
            if (numChildren == 0) {
                return Collections.emptyList();
            } else {
                final List<T> children = new ArrayList<>();
                for (short i = 1; i <= numChildren; i++) {
                    final var childTxnId = txnId.toBuilder().setNonce(i).build();
                    final var childRecord = getPriorityRecord(childTxnId);
                    /* It will be extraordinarily rare for a parent record to still be available
                    while a child has expired. But it could happen, so we silently work around it. */
                    if (childRecord != null) {
                        children.add(transform.apply(childRecord));
                    }
                }
                return children;
            }
        }
    }

    private List<TransactionRecord> duplicatesOf(final TransactionID txnId) {
        final var recentHistory = histories.get(txnId);
        if (recentHistory == null) {
            return Collections.emptyList();
        } else {
            return recentHistory.allDuplicateRecords().stream()
                    .map(ExpirableTxnRecord::asGrpc)
                    .toList();
        }
    }

    private TxnReceipt receiptFrom(final TxnIdRecentHistory recentHistory) {
        return Optional.ofNullable(recentHistory.priorityRecord())
                .map(ExpirableTxnRecord::getReceipt)
                .orElse(UNKNOWN_RECEIPT);
    }

    public ExpirableTxnRecord getPriorityRecord(final TransactionID txnId) {
        final var history = histories.get(txnId);
        if (history != null) {
            return Optional.ofNullable(history.priorityRecord()).orElse(null);
        }
        return null;
    }
}
