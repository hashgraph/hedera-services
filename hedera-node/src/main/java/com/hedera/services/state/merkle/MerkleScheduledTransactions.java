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
package com.hedera.services.state.merkle;

import com.google.common.base.MoreObjects;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualKey;
import com.hedera.services.state.virtual.schedule.ScheduleEqualityVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleSecondVirtualValue;
import com.hedera.services.state.virtual.schedule.ScheduleVirtualValue;
import com.hedera.services.state.virtual.schedule.WritableCopyable;
import com.hedera.services.state.virtual.temporal.SecondSinceEpocVirtualKey;
import com.swirlds.common.Copyable;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialNaryMerkleInternal;
import com.swirlds.jasperdb.JasperDbBuilder;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.List;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class MerkleScheduledTransactions extends PartialNaryMerkleInternal
        implements MerkleInternal {
    private static final Logger log = LogManager.getLogger(MerkleScheduledTransactions.class);

    static Runnable stackDump = Thread::dumpStack;

    public static final int RELEASE_0270_VERSION = 1;

    public static final int RELEASE_0230_VERSION = 2;
    static final int CURRENT_VERSION = RELEASE_0230_VERSION;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x451acf2156692908L;

    /* Order of Merkle node children */
    public static final class ChildIndices {
        static final int STATE = 0;
        static final int BY_ID = 1;
        static final int BY_EXPIRATION_SECOND = 2;
        static final int BY_EQUALITY = 3;
        static final int NUM_0270_CHILDREN = 4;

        ChildIndices() {
            throw new UnsupportedOperationException("Utility Class");
        }
    }

    public MerkleScheduledTransactions(
            final List<MerkleNode> children,
            final MerkleScheduledTransactions immutableMerkleScheduledTransactions) {
        super(immutableMerkleScheduledTransactions);
        addDeserializedChildren(children, CURRENT_VERSION);
    }

    public MerkleScheduledTransactions(final List<MerkleNode> children) {
        super(ChildIndices.NUM_0270_CHILDREN);
        addDeserializedChildren(children, CURRENT_VERSION);
    }

    public MerkleScheduledTransactions() {
        final var virtualMapFactory = getVirtualMapFactory();
        addDeserializedChildren(
                List.of(
                        new MerkleScheduledTransactionsState(),
                        virtualMapFactory.newScheduleListStorage(),
                        virtualMapFactory.newScheduleTemporalStorage(),
                        virtualMapFactory.newScheduleEqualityStorage()),
                CURRENT_VERSION);
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
            final var msg =
                    String.format(
                            "Copy called on immutable MerkleScheduledTransactions by thread '%s'!",
                            Thread.currentThread().getName());
            log.warn(msg);
            /* Ensure we get this stack trace in case a caller incorrectly suppresses the exception. */
            stackDump.run();
            throw new IllegalStateException(
                    "Tried to make a copy of an immutable MerkleScheduledTransactions!");
        }

        setImmutable(true);
        return new MerkleScheduledTransactions(
                List.of(
                        getCopyable(ChildIndices.STATE).copy(),
                        getCopyable(ChildIndices.BY_ID).copy(),
                        getCopyable(ChildIndices.BY_EXPIRATION_SECOND).copy(),
                        getCopyable(ChildIndices.BY_EQUALITY).copy()),
                this);
    }

    /* ---- Object ---- */

    // We intentionally don't implement equals and hashcode here.

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(MerkleScheduledTransactions.class)
                .add("state", state())
                .add("# schedules", getChildSize(ChildIndices.BY_ID))
                .add("# seconds", getChildSize(ChildIndices.BY_EXPIRATION_SECOND))
                .add("# equalities", getChildSize(ChildIndices.BY_EQUALITY))
                .toString();
    }

    /* ----  Merkle children  ---- */
    public MerkleScheduledTransactionsState state() {
        return getChild(ChildIndices.STATE);
    }

    public VirtualMap<EntityNumVirtualKey, ScheduleVirtualValue> byId() {
        return maybeMigrateMap(
                ChildIndices.BY_ID, () -> getVirtualMapFactory().newScheduleListStorage());
    }

    public VirtualMap<SecondSinceEpocVirtualKey, ScheduleSecondVirtualValue> byExpirationSecond() {
        return maybeMigrateMap(
                ChildIndices.BY_EXPIRATION_SECOND,
                () -> getVirtualMapFactory().newScheduleTemporalStorage());
    }

    public VirtualMap<ScheduleEqualityVirtualKey, ScheduleEqualityVirtualValue> byEquality() {
        return maybeMigrateMap(
                ChildIndices.BY_EQUALITY,
                () -> getVirtualMapFactory().newScheduleEqualityStorage());
    }

    public long getNumSchedules() {
        return getChildSize(ChildIndices.BY_ID);
    }

    public long getCurrentMinSecond() {
        return state().currentMinSecond();
    }

    public void setCurrentMinSecond(final long currentMinSecond) {
        throwIfImmutable(
                "Cannot change this MerkleScheduledTransactions' currentMinSecond if it's"
                        + " immutable.");
        state().setCurrentMinSecond(currentMinSecond);
    }

    public void do0230MigrationIfNeeded() {
        if ((getChild(ChildIndices.BY_ID) instanceof MerkleMap<?, ?>)
                || (getChild(ChildIndices.BY_EXPIRATION_SECOND) instanceof MerkleMap<?, ?>)
                || (getChild(ChildIndices.BY_EQUALITY) instanceof MerkleMap<?, ?>)) {
            byId();
            byExpirationSecond();
            byEquality();
        }
    }

    private Copyable getCopyable(int childIdx) {
        return getChild(childIdx);
    }

    private long getChildSize(int childIdx) {
        Object child = getChild(childIdx);

        if (child instanceof MerkleMap<?, ?>) {
            return ((MerkleMap<?, ?>) child).size();
        } else if (child instanceof VirtualMap<?, ?>) {
            return ((VirtualMap<?, ?>) child).size();
        }

        return 0;
    }

    private <K extends VirtualMap<?, ? extends WritableCopyable>> K maybeMigrateMap(
            int childIdx, Supplier<K> newChildSup) {

        Object child = getChild(childIdx);
        if (child instanceof MerkleMap<?, ?>) {

            if (isImmutable()) {
                throw new IllegalStateException(
                        "Migration not complete for child "
                                + childIdx
                                + " and this object is immutable.");
            }

            MerkleMap<?, ?> merkle = uncheckedCast(child);
            var newChild = newChildSup.get();
            child = newChild;
            merkle.forEach(
                    (k, v) -> {
                        WritableCopyable value = uncheckedCast(v);
                        newChild.put(uncheckedCast(k), value.asWritable());
                    });
            setChild(childIdx, newChild);
        }

        return uncheckedCast(child);
    }

    private static VirtualMapFactory getVirtualMapFactory() {
        return new VirtualMapFactory(JasperDbBuilder::new);
    }

    @SuppressWarnings("unchecked")
    private static <T> T uncheckedCast(Object obj) {
        return (T) obj;
    }
}
