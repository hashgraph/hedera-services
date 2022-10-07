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
package com.hedera.services.stream;

import static com.hedera.services.ServicesState.EMPTY_HASH;

import com.google.common.annotations.VisibleForTesting;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang3.builder.EqualsBuilder;

/**
 * Contains current {@code com.swirlds.common.crypto.RunningHash} which contains a Hash which is a
 * running Hash calculated from all {@link RecordStreamObject} in history
 */
public class RecordsRunningHashLeaf extends PartialMerkleLeaf implements MerkleLeaf {
    static final long CLASS_ID = 0xe370929ba5429d9bL;
    static final int CLASS_VERSION = 1;

    static final int RELEASE_0280_VERSION = 2;
    /** a runningHash of all RecordStreamObject */
    private RunningHash runningHash;
    /**
     * runningHash of the previous RecordStreamObjects. They are needed for the UtilPrng transaction
     */
    private RunningHash nMinus1RunningHash;

    private RunningHash nMinus2RunningHash;
    private RunningHash nMinus3RunningHash;

    /** no-args constructor required by ConstructableRegistry */
    public RecordsRunningHashLeaf() {}

    public RecordsRunningHashLeaf(final RunningHash runningHash) {
        this.runningHash = runningHash;
        resetMinusHashes(true);
    }

    private RecordsRunningHashLeaf(final RecordsRunningHashLeaf runningHashLeaf) {
        this.runningHash = runningHashLeaf.runningHash;
        this.nMinus1RunningHash = runningHashLeaf.nMinus1RunningHash;
        this.nMinus2RunningHash = runningHashLeaf.nMinus2RunningHash;
        this.nMinus3RunningHash = runningHashLeaf.nMinus3RunningHash;

        setImmutable(false);
        runningHashLeaf.setImmutable(true);
        setHash(runningHashLeaf.getHash());
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        try {
            // should wait until runningHash has been calculated and set
            out.writeSerializable(currentRunningHash(), true);
            // It is guaranteed that the futureHash's of the preceding hashes will
            // always be set once the current runningHash's future is set
            out.writeSerializable(nMinus1RunningHash.getHash(), true);
            out.writeSerializable(nMinus2RunningHash.getHash(), true);
            out.writeSerializable(nMinus3RunningHash.getHash(), true);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(
                    "Got interrupted when getting runningHash when serializing RunningHashLeaf", e);
        }
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        runningHash = new RunningHash();
        runningHash.setHash(in.readSerializable());

        if (version >= RELEASE_0280_VERSION) {
            resetMinusHashes(false);
            nMinus1RunningHash.setHash(in.readSerializable());
            nMinus2RunningHash.setHash(in.readSerializable());
            nMinus3RunningHash.setHash(in.readSerializable());
        } else {
            resetMinusHashes(true);
        }
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final var that = (RecordsRunningHashLeaf) o;
        if (areHashesPresent(that)) {
            return areHashesEqual(that);
        }
        return new EqualsBuilder()
                .append(this.runningHash, that.runningHash)
                .append(this.nMinus1RunningHash, that.nMinus1RunningHash)
                .append(this.nMinus2RunningHash, that.nMinus2RunningHash)
                .append(this.nMinus3RunningHash, that.nMinus3RunningHash)
                .isEquals();
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(
                runningHash, nMinus1RunningHash, nMinus2RunningHash, nMinus3RunningHash);
    }

    public RecordsRunningHashLeaf copy() {
        return new RecordsRunningHashLeaf(this);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return RELEASE_0280_VERSION;
    }

    @Override
    public String toString() {
        return String.format(
                "RecordsRunningHashLeaf's Hash: %s, Hash contained in the leaf: %s, "
                        + "nMinus1RunningHash: %s, nMinus2RunningHash: %s, nMinus3RunningHash: %s",
                getHash(),
                runningHash.getHash(),
                nMinus1RunningHash.getHash(),
                nMinus2RunningHash.getHash(),
                nMinus3RunningHash.getHash());
    }

    public RunningHash getRunningHash() {
        return runningHash;
    }

    public RunningHash getNMinus3RunningHash() {
        return nMinus3RunningHash;
    }

    public void setRunningHash(final RunningHash runningHash) {
        // update the previous running hashes
        nMinus3RunningHash = nMinus2RunningHash;
        nMinus2RunningHash = nMinus1RunningHash;
        nMinus1RunningHash = this.runningHash;
        this.runningHash = runningHash;
        // should invalidate current Hash when updating the runningHash object
        // because its Hash should be calculated based on the runningHash object
        this.invalidateHash();
    }

    public Hash currentRunningHash() throws InterruptedException {
        try {
            return runningHash.getFutureHash().get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to get current running hash", e);
        }
    }

    public Hash nMinusThreeRunningHash() throws InterruptedException {
        try {
            return nMinus3RunningHash.getFutureHash().get();
        } catch (ExecutionException e) {
            throw new IllegalStateException("Unable to get n-3 running hash", e);
        }
    }

    private boolean areHashesPresent(final RecordsRunningHashLeaf that) {
        return this.runningHash.getHash() != null
                && that.runningHash.getHash() != null
                && this.nMinus1RunningHash.getHash() != null
                && that.nMinus1RunningHash.getHash() != null
                && this.nMinus2RunningHash.getHash() != null
                && that.nMinus2RunningHash.getHash() != null
                && this.nMinus3RunningHash.getHash() != null
                && that.nMinus3RunningHash.getHash() != null;
    }

    private boolean areHashesEqual(final RecordsRunningHashLeaf that) {
        return this.runningHash.getHash().equals(that.runningHash.getHash())
                && this.nMinus1RunningHash.getHash().equals(that.nMinus1RunningHash.getHash())
                && this.nMinus2RunningHash.getHash().equals(that.nMinus2RunningHash.getHash())
                && this.nMinus3RunningHash.getHash().equals(that.nMinus3RunningHash.getHash());
    }

    private void resetMinusHashes(final boolean alreadyCompleted) {
        nMinus1RunningHash = alreadyCompleted ? new RunningHash(EMPTY_HASH) : new RunningHash();
        nMinus2RunningHash = alreadyCompleted ? new RunningHash(EMPTY_HASH) : new RunningHash();
        nMinus3RunningHash = alreadyCompleted ? new RunningHash(EMPTY_HASH) : new RunningHash();
    }

    @VisibleForTesting
    public RunningHash getNMinus2RunningHash() {
        return nMinus2RunningHash;
    }

    @VisibleForTesting
    public RunningHash getNMinus1RunningHash() {
        return nMinus1RunningHash;
    }
}
