/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.node.app.service.mono.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.merkle.map.MerkleMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MerkleScheduledTransactions extends PartialNaryMerkleInternal implements MerkleInternal {
    private static final Logger log = LogManager.getLogger(MerkleScheduledTransactions.class);

    private int pendingMigrationSize;

    static Runnable stackDump = Thread::dumpStack;

    public static final int RELEASE_0270_VERSION = 1;
    static final int CURRENT_VERSION = RELEASE_0270_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x451acf2156692908L;

    /* Order of Merkle node children */
    public static final class ChildIndices {
        private static final int STATE = 0;
        static final int BY_ID = 1;
        private static final int BY_EXPIRATION_SECOND = 2;
        private static final int BY_EQUALITY = 3;
        static final int NUM_0270_CHILDREN = 4;

        ChildIndices() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    public MerkleScheduledTransactions(
            final List<MerkleNode> children, final MerkleScheduledTransactions immutableMerkleScheduledTransactions) {
        super(immutableMerkleScheduledTransactions);
        addDeserializedChildren(children, CURRENT_VERSION);
    }

    public MerkleScheduledTransactions(final List<MerkleNode> children) {
        super(ChildIndices.NUM_0270_CHILDREN);
        addDeserializedChildren(children, CURRENT_VERSION);
    }

    public MerkleScheduledTransactions() {
        addDeserializedChildren(
                List.of(
                        new MerkleScheduledTransactionsState(),
                        new MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue>(),
                        new MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue>(),
                        new MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue>()),
                CURRENT_VERSION);
    }

    /**
     * @param pendingMigrationSize the size of the legacy schedules map
     * @deprecated remove once 0.27 migration is no longer needed
     */
    @Deprecated(since = "0.27")
    public MerkleScheduledTransactions(int pendingMigrationSize) {
        this();
        this.pendingMigrationSize = pendingMigrationSize;
    }

    /* --- MerkleInternal --- */
    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public int getMinimumChildCount() {
        return ChildIndices.NUM_0270_CHILDREN;
    }

    /* --- FastCopyable --- */
    @Override
    public MerkleScheduledTransactions copy() {
        if (isImmutable()) {
            final var msg = String.format(
                    "Copy called on immutable MerkleScheduledTransactions by thread '%s'!",
                    Thread.currentThread().getName());
            log.warn(msg);
            /* Ensure we get this stack trace in case a caller incorrectly suppresses the exception. */
            stackDump.run();
            throw new IllegalStateException("Tried to make a copy of an immutable MerkleScheduledTransactions!");
        }

        setImmutable(true);
        return new MerkleScheduledTransactions(
                List.of(
                        state().copy(),
                        byId().copy(),
                        byExpirationSecond().copy(),
                        byEquality().copy()),
                this);
    }

    /* ---- Object ---- */

    // We intentionally don't implement equals and hashcode here.

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleScheduledTransactions.class)
                .add("state", state())
                .add("# schedules", byId().size())
                .add("# seconds", byExpirationSecond().size())
                .add("# equalities", byEquality().size())
                .toString();
    }

    /* ----  Merkle children  ---- */
    public MerkleScheduledTransactionsState state() {
        return getChild(ChildIndices.STATE);
    }

    public MerkleMap<EntityNumVirtualKey, ScheduleVirtualValue> byId() {
        return getChild(ChildIndices.BY_ID);
    }

    public MerkleMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond() {
        return getChild(ChildIndices.BY_EXPIRATION_SECOND);
    }

    public MerkleMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality() {
        return getChild(ChildIndices.BY_EQUALITY);
    }

    public long getNumSchedules() {
        var byIdSize = byId().size();
        if (pendingMigrationSize > 0 && byIdSize <= 0) {
            return pendingMigrationSize;
        }
        return byIdSize;
    }

    public long getCurrentMinSecond() {
        return state().currentMinSecond();
    }

    public void setCurrentMinSecond(final long currentMinSecond) {
        throwIfImmutable("Cannot change this MerkleScheduledTransactions' currentMinSecond if it's" + " immutable.");
        state().setCurrentMinSecond(currentMinSecond);
    }
}
