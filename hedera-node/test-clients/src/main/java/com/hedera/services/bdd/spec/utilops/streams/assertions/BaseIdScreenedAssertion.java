/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.TransactionID;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A small starter class for assertions that match a record stream item by {@code transactionID}, and
 * then assert something about the body or record in the item.
 *
 * <p>This class does not restrict to id's with {@code nonce=0} or with {@code scheduled=false} (i.e. it
 * includes items for both the given transaction id and all its child items); but subclasses can do so
 * if desired by overriding * the {@link #filter(TransactionID)} method.
 */
public abstract class BaseIdScreenedAssertion implements RecordStreamAssertion {
    private final String specTxnId;
    protected final HapiSpec spec;

    protected BaseIdScreenedAssertion(@NonNull final String specTxnId, @NonNull final HapiSpec spec) {
        this.specTxnId = specTxnId;
        this.spec = spec;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isApplicableTo(@NonNull final RecordStreamItem item) {
        final var maybeTxnId = spec.registry().getMaybeTxnId(specTxnId);
        if (maybeTxnId.isEmpty()) {
            return false;
        }
        final var txnId = maybeTxnId.get();
        final var observedId = item.getRecord().getTransactionID();
        return baseFieldsMatch(txnId, observedId) && filter(observedId);
    }

    /**
     * Allows subclasses to further refine the filtering of record stream items by {@code TransactionID};
     * for example, a subclass might only be interested when the {@code TransactionID} has a particular
     * {@code nonce} value.
     *
     * @param txnId the {@code TransactionID} of the record stream item
     * @return whether if the item should be included in the assertion
     */
    protected boolean filter(@NonNull final TransactionID txnId) {
        return true;
    }

    /**
     * Determines if the transaction valid start and account id of the two {@code TransactionID}'s match.
     *
     * @param a the first {@code TransactionID}
     * @param b the second {@code TransactionID}
     * @return whether the base fields of the two {@code TransactionID}'s match
     */
    static boolean baseFieldsMatch(@NonNull final TransactionID a, @NonNull final TransactionID b) {
        return a.getTransactionValidStart().equals(b.getTransactionValidStart())
                && a.getAccountID().equals(b.getAccountID());
    }
}
