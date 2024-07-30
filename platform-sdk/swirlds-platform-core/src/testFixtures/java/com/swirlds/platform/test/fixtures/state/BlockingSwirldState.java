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

package com.swirlds.platform.test.fixtures.state;

import static com.swirlds.common.threading.interrupt.Uninterruptable.abortAndThrowIfInterrupted;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.merkle.route.MerkleRoute;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A dummy swirld state for SignedStateManager unit tests.
 */
public class BlockingSwirldState extends PartialMerkleLeaf implements MerkleLeaf, SwirldState, MerkleRoot {

    private static final long DEFAULT_UNIT_TEST_SECS = 10;

    // The version history of this class.
    // Versions that have been released must NEVER be given a different value.
    /**
     * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
     * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
     * specially by the platform.
     */
    private static final int VERSION_ORIGINAL = 1;
    /**
     * In this version, serialization was performed by serialize/deserialize.
     */
    private static final int VERSION_MIGRATE_TO_SERIALIZABLE = 2;

    private static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

    private static final long CLASS_ID = 0xa7d6e4b5feda7ce5L;
    private PlatformState platformState;

    private CountDownLatch serializationLatch;

    protected volatile boolean allowDeletion = true;

    protected CountDownLatch deletionLatch = new CountDownLatch(1);

    protected AtomicBoolean released = new AtomicBoolean(false);

    public BlockingSwirldState() {
        super();
    }

    public BlockingSwirldState(final PlatformState platformState) {
        this.platformState = platformState;
    }

    /**
     * Protection should always be enabled but current unit tests don't expect this behavior
     *
     * @param protectionEnabled If protection is enabled then this SignedState can only be deleted after explicitly
     *                          enabled.
     */
    public BlockingSwirldState(final boolean protectionEnabled) {
        allowDeletion = !protectionEnabled;
    }

    private BlockingSwirldState(final BlockingSwirldState that) {
        super(that);
        this.platformState = that.platformState;
        this.allowDeletion = that.allowDeletion;
        this.deletionLatch = that.deletionLatch;
        this.released = new AtomicBoolean(that.released.get());
    }

    @Override
    public void handleConsensusRound(final Round round, final PlatformState platformState) {
        // intentionally does nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlockingSwirldState copy() {
        throwIfImmutable();
        return new BlockingSwirldState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof final BlockingSwirldState that)) {
            return false;
        }
        return Objects.equals(
                this.getPlatformState().getAddressBook(),
                that.getPlatformState().getAddressBook());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }

    /**
     * If called, the next serialization attempt will block until {@link #unblockSerialization()} has been called.
     */
    public void enableBlockingSerialization() {
        serializationLatch = new CountDownLatch(1);
    }

    /**
     * Should only be called if {@link #enableBlockingSerialization()} has previously been called.
     */
    public void unblockSerialization() {
        serializationLatch.countDown();
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    public PlatformState getPlatformState() {
        return platformState;
    }

    /**
     * {@inheritDoc}
     */
    public void setPlatformState(@NonNull PlatformState platformState) {
        this.platformState = platformState;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public SwirldState getSwirldState() {
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public String getInfoString(int hashDepth) {
        return "<test info string>";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getNumberOfChildren() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T extends MerkleNode> T getChild(int index) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChild(int index, MerkleNode child, MerkleRoute childRoute, boolean childMayBeImmutable) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        if (serializationLatch != null) {
            abortAndThrowIfInterrupted(serializationLatch::await, "interrupted while waiting for latch");
        }
        out.writeSerializable(platformState, true);
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        platformState = in.readSerializable();
    }

    public void enableDeletion() {
        allowDeletion = true;
    }

    public void disableDeletion() {
        allowDeletion = false;
    }

    public void waitForDeletion() {
        try {
            // 10 seconds is assumed to be more than sufficient for any unit test. If a test requires
            // a greater wait then a variable timeout parameter can be added.
            assertTrue(
                    deletionLatch.await(DEFAULT_UNIT_TEST_SECS, TimeUnit.SECONDS),
                    "Unit test took longer than the default of 10 seconds. Fix the test or override the wait time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        }
    }

    public void setAddressBook(AddressBook addressBook) {
        platformState.setAddressBook(addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyNode() {
        if (!allowDeletion) {
            fail("State is not allowed to be deleted");
        }
        if (!released.compareAndSet(false, true)) {
            throw new IllegalStateException("This type of node should only be deleted once");
        }
        deletionLatch.countDown();
    }
}
