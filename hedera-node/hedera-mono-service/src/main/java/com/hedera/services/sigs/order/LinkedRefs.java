/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.order;

import static com.hedera.services.ledger.SigImpactHistorian.ChangeStatus.UNCHANGED;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.hedera.services.ledger.SigImpactHistorian;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LinkedRefs {
    private static final int EXPECTED_LINKED_NUMS = 1;

    private int i = 0;
    private Instant sourceSignedAt = Instant.EPOCH;
    private long[] linkedNums = new long[EXPECTED_LINKED_NUMS];
    private List<ByteString> linkedAliases = null;

    public LinkedRefs() {
        // No-op
    }

    public LinkedRefs(final Instant sourceSignedAt) {
        this.sourceSignedAt = sourceSignedAt;
    }

    public boolean haveNoChangesAccordingTo(final SigImpactHistorian historian) {
        for (int j = 0; j < linkedNums.length && linkedNums[j] != 0; j++) {
            if (historian.entityStatusSince(sourceSignedAt, linkedNums[j]) != UNCHANGED) {
                return false;
            }
        }
        if (linkedAliases != null) {
            for (final var alias : linkedAliases) {
                if (historian.aliasStatusSince(sourceSignedAt, alias) != UNCHANGED) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Records a given alias as linked to a transaction. Important when expanding signatures from a
     * signed state, since during {@code handleTransaction()} we must re-expand signatures if any
     * alias used during {@code expandSignatures()} has changed since the source state was signed.
     *
     * @param alias the alias to record as linked
     */
    public void link(final ByteString alias) {
        if (linkedAliases == null) {
            linkedAliases = new ArrayList<>();
        }
        linkedAliases.add(alias);
    }

    /**
     * Records a given entity num as linked to a transaction. Important when expanding signatures
     * from a signed state, since during {@code handleTransaction()} we must re-expand signatures if
     * any entity key used during {@code expandSignatures()} has changed since the source state was
     * signed.
     *
     * @param num the entity number to record as linked
     */
    public void link(final long num) {
        /* Only positive entity numbers can possibly change, so just ignore anything else. */
        if (num <= 0) {
            return;
        }
        if (i == linkedNums.length) {
            linkedNums = Arrays.copyOf(linkedNums, 2 * linkedNums.length);
        }
        linkedNums[i++] = num;
    }

    /**
     * Returns all entity numbers recorded as linked to a transaction in an array that may contain
     * padding zeroes on the right. (Where zeroes should be ignored.)
     *
     * @return a possibly zero-padded array of the linked entity numbers
     */
    public long[] linkedNumbers() {
        return linkedNums;
    }

    /**
     * Returns all aliases recorded as linked to a transaction.
     *
     * @return the linked aliases
     */
    public List<ByteString> linkedAliases() {
        return linkedAliases == null ? Collections.emptyList() : linkedAliases;
    }

    public Instant getSourceSignedAt() {
        return sourceSignedAt;
    }

    public void setSourceSignedAt(final Instant sourceSignedAt) {
        this.sourceSignedAt = sourceSignedAt;
    }

    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("sourceSignedAt", sourceSignedAt)
                .add("linkedAliases", linkedAliases)
                .add("linkedNums", linkedNums)
                .toString();
    }
}
