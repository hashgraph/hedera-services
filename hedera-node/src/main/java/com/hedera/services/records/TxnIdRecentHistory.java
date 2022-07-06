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

import static com.hedera.services.txns.diligence.DuplicateClassification.BELIEVED_UNIQUE;
import static com.hedera.services.txns.diligence.DuplicateClassification.DUPLICATE;
import static com.hedera.services.txns.diligence.DuplicateClassification.NODE_DUPLICATE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NODE_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PAYER_SIGNATURE;
import static java.util.Comparator.comparing;
import static java.util.Comparator.comparingLong;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.txns.diligence.DuplicateClassification;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Manages the recent history of a {@code TransactionID}. This history consists of records, where
 * each record gives the result of a transaction reaching consensus and being handled. There are two
 * types of records:
 *
 * <ol>
 *   <li><b>Unclassifiable records</b> - records whose final status was {@code INVALID_NODE_ACCOUNT}
 *       or {@code INVALID_PAYER_SIGNATURE}. These statuses are inherently suspicious, and suggest a
 *       malicious node (since under normal conditions a node should never submit a transaction that
 *       ends with either status). So they do not cause other transactions to be classified as a
 *       duplicate.
 *   <li><b>Classifiable records</b> - records whose final status was anything other than the two
 *       suspicious results above. All classifiable records after the first are either
 *       <i>duplicates</i> or <i>node duplicates</i>. (A node duplicate is a duplicate that reached
 *       consensus <i>after</i> a previous classifiable transaction submitted by the same node.)
 * </ol>
 *
 * <p>The implementation uses two linked lists of records, one for classifiable records and one for
 * unclassifiable. We ensure node duplicates can be identified in constant time (depending only on
 * the number of nodes in the network) by keeping all the non-node duplicates at the start of the
 * classifiable record list in a prefix of length {@code numDuplicatesFromDifferentNodes}.
 *
 * <p>So to classify a new record from node X as either unique, duplicate, or node duplicate, we
 * only need to iterate through the first {@code numDuplicatesFromDifferentNodes} records in the
 * classifiable list. If none of these records were submitted by node X, the new record cannot be a
 * node duplicate. We then insert it at position {@code numDuplicatesFromDifferentNodes}, and
 * increment {@code numDuplicatesFromDifferentNodes++}. (If any <i>were</i> submitted by node X, of
 * course the new record is a node duplicate; and we just add it to the very end of the classifiable
 * list, leaving {@code numDuplicatesFromDifferentNodes} unchanged.)
 */
public class TxnIdRecentHistory {
    private static final Comparator<RichInstant> RI_CMP =
            comparingLong(RichInstant::getSeconds).thenComparingInt(RichInstant::getNanos);
    private static final Comparator<ExpirableTxnRecord> CONSENSUS_TIME_COMPARATOR =
            comparing(ExpirableTxnRecord::getConsensusTime, RI_CMP);

    private int numDuplicatesFromDifferentNodes = 0;

    List<ExpirableTxnRecord> memory = null;
    List<ExpirableTxnRecord> classifiableRecords = null;
    List<ExpirableTxnRecord> unclassifiableRecords = null;

    private static final Set<ResponseCodeEnum> UNCLASSIFIABLE_STATUSES =
            EnumSet.of(INVALID_NODE_ACCOUNT, INVALID_PAYER_SIGNATURE);

    /**
     * Returns the "highest-priority" record with this transaction id, which is defined as either
     * the oldest classifiable record, if one exists; or the oldest unclassifiable record, if one
     * exists.
     *
     * @return the highest-priority record if any exists, null otherwise
     */
    public ExpirableTxnRecord priorityRecord() {
        if (areForgotten(classifiableRecords)) {
            return areForgotten(unclassifiableRecords) ? null : unclassifiableRecords.get(0);
        } else {
            return classifiableRecords.get(0);
        }
    }

    /**
     * Returns all the duplicate records in this recent history, ordered by consensus time.
     *
     * @return all the duplicate historical records in chronological order
     */
    public List<ExpirableTxnRecord> allDuplicateRecords() {
        return Stream.concat(duplicateClassifiableRecords(), duplicateUnclassifiableRecords())
                .sorted(CONSENSUS_TIME_COMPARATOR)
                .toList();
    }

    /**
     * Indicates if there are any records in this recent history.
     *
     * @return true if there are no records in the recent history, false otherwise
     */
    public boolean isForgotten() {
        return areForgotten(classifiableRecords) && areForgotten(unclassifiableRecords);
    }

    /**
     * Adds a record to the recent history based on the provided final status of {@code
     * handleTransaction}.
     *
     * @param expirableTxnRecord a record belonging to this history
     * @param status the final status of the associated transaction
     */
    public void observe(
            final ExpirableTxnRecord expirableTxnRecord, final ResponseCodeEnum status) {
        if (UNCLASSIFIABLE_STATUSES.contains(status)) {
            addUnclassifiable(expirableTxnRecord);
        } else {
            addClassifiable(expirableTxnRecord);
        }
    }

    /**
     * Used during a reconnect or restart to "stage" a collection of records which can then be
     * sorted by consensus time and replayed with a call to {@link
     * TxnIdRecentHistory#observeStaged()}. This is critical so that the reconnected node will
     * classify any future records in exactly the same way as nodes that did not reconnect.
     *
     * @param unorderedRecord a record from a saved state that belongs to this recent history
     */
    public void stage(final ExpirableTxnRecord unorderedRecord) {
        if (memory == null) {
            memory = new ArrayList<>();
        }
        memory.add(unorderedRecord);
    }

    /**
     * Replays all the records given to {@link TxnIdRecentHistory#stage} as if they had been given
     * to {@link TxnIdRecentHistory#observe(ExpirableTxnRecord, ResponseCodeEnum)} in consensus
     * order.
     */
    public void observeStaged() {
        memory.sort(CONSENSUS_TIME_COMPARATOR);
        memory.forEach(
                expirableTxnRecord ->
                        this.observe(
                                expirableTxnRecord,
                                ResponseCodeEnum.valueOf(
                                        expirableTxnRecord.getReceipt().getStatus())));
        memory = null;
    }

    /**
     * Expires from the history any record with an expiration that is <b>not after</b> the given
     * consensus second.
     *
     * @param now the current consensus second
     */
    public void forgetExpiredAt(final long now) {
        if (classifiableRecords != null) {
            forgetFromClassifiableList(now);
        }
        if (unclassifiableRecords != null) {
            forgetFromUnclassifiableList(now);
        }
    }

    /**
     * Classifies the duplicate status that a new classifiable record from the given member would
     * receive, given the current history.
     *
     * @param submittingMember a node id
     * @return the duplicate status of a classifiable record from the given node
     */
    public DuplicateClassification currentDuplicityFor(final long submittingMember) {
        if (numDuplicatesFromDifferentNodes == 0) {
            return BELIEVED_UNIQUE;
        }
        final var iter = classifiableRecords.listIterator();
        for (int i = 0; i < numDuplicatesFromDifferentNodes; i++) {
            if (iter.next().getSubmittingMember() == submittingMember) {
                return NODE_DUPLICATE;
            }
        }
        return DUPLICATE;
    }

    /* --- Internal helpers --- */
    private Stream<ExpirableTxnRecord> duplicateClassifiableRecords() {
        if (areForgotten(classifiableRecords) || classifiableRecords.size() == 1) {
            return Stream.empty();
        } else {
            return classifiableRecords.subList(1, classifiableRecords.size()).stream();
        }
    }

    private Stream<ExpirableTxnRecord> duplicateUnclassifiableRecords() {
        final var startIndex = areForgotten(classifiableRecords) ? 1 : 0;
        if (areForgotten(unclassifiableRecords) || unclassifiableRecords.size() <= startIndex) {
            return Stream.empty();
        } else {
            return unclassifiableRecords.subList(startIndex, unclassifiableRecords.size()).stream();
        }
    }

    private void addClassifiable(final ExpirableTxnRecord expirableTxnRecord) {
        if (classifiableRecords == null) {
            classifiableRecords = new LinkedList<>();
        }
        int i = 0;
        final var submittingMember = expirableTxnRecord.getSubmittingMember();
        final var iter = classifiableRecords.listIterator();
        boolean isNodeDuplicate = false;
        while (i < numDuplicatesFromDifferentNodes) {
            if (submittingMember == iter.next().getSubmittingMember()) {
                isNodeDuplicate = true;
                break;
            }
            i++;
        }
        if (isNodeDuplicate) {
            classifiableRecords.add(expirableTxnRecord);
        } else {
            numDuplicatesFromDifferentNodes++;
            iter.add(expirableTxnRecord);
        }
    }

    private void addUnclassifiable(final ExpirableTxnRecord expirableTxnRecord) {
        if (unclassifiableRecords == null) {
            unclassifiableRecords = new LinkedList<>();
        }
        unclassifiableRecords.add(expirableTxnRecord);
    }

    private boolean areForgotten(final List<ExpirableTxnRecord> records) {
        return records == null || records.isEmpty();
    }

    private void forgetFromUnclassifiableList(final long now) {
        final var size = unclassifiableRecords.size();
        if (size > 1) {
            unclassifiableRecords.removeIf(
                    expirableTxnRecord -> expirableTxnRecord.getExpiry() <= now);
        } else if (size == 1) {
            final var onlyRecord = unclassifiableRecords.get(0);
            if (onlyRecord.getExpiry() <= now) {
                unclassifiableRecords.clear();
            }
        }
    }

    private void forgetFromClassifiableList(final long now) {
        final var size = classifiableRecords.size();
        if (size > 1) {
            final var iter = classifiableRecords.iterator();
            var discardedDuplicatesFromDifferentNodes = 0;
            for (int i = 0; iter.hasNext(); i++) {
                final var nextRecord = iter.next();
                if (nextRecord.getExpiry() <= now) {
                    iter.remove();
                    if (i < numDuplicatesFromDifferentNodes) {
                        discardedDuplicatesFromDifferentNodes++;
                    }
                }
            }
            numDuplicatesFromDifferentNodes -= discardedDuplicatesFromDifferentNodes;
        } else if (size == 1) {
            final var onlyRecord = classifiableRecords.get(0);
            if (onlyRecord.getExpiry() <= now) {
                classifiableRecords.clear();
                numDuplicatesFromDifferentNodes = 0;
            }
        }
    }
}
