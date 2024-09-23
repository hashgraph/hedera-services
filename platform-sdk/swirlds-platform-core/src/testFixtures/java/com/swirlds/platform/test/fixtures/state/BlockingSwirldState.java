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
import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.MerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.state.merkle.singleton.StringLeaf;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A test implementation of {@link MerkleRoot} and {@link SwirldState} state for SignedStateManager unit tests.
 * Node that some of the {@link MerkleRoot} methods are intentionally not implemented. If a test needs these methods,
 * {@link com.swirlds.platform.state.MerkleStateRoot} should be used instead.
 */
public class BlockingSwirldState extends MerkleStateRoot {

    static {
        try {
            ConstructableRegistry.getInstance()
                    .registerConstructable(new ClassConstructorPair(BlockingStringLeaf.class, BlockingStringLeaf::new));
        } catch (ConstructableRegistryException e) {
            throw new RuntimeException(e);
        }
    }
    /**
     * In this version, serialization was performed by serialize/deserialize.
     */
    private static final int VERSION_MIGRATE_TO_SERIALIZABLE = MerkleStateRoot.CURRENT_VERSION;

    private static final int CLASS_VERSION = VERSION_MIGRATE_TO_SERIALIZABLE;

    private static final long CLASS_ID = 0xa7d6e4b5feda7ce5L;

    private final BlockingStringLeaf value;

    /**
     * Constructs a new instance of {@link BlockingSwirldState}.
     */
    public BlockingSwirldState() {
        super(FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(version.major()));
        value = new BlockingStringLeaf();
        setChild(1, value);
    }

    private BlockingSwirldState(final BlockingSwirldState that) {
        super(that);
        this.value = that.value;
        setChild(1, value);
    }

    @Override
    public void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {
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
                this.getReadablePlatformState().getAddressBook(),
                that.getReadablePlatformState().getAddressBook());
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
        value.enableBlockingSerialization();
    }

    /**
     * Should only be called if {@link #enableBlockingSerialization()} has previously been called.
     */
    public void unblockSerialization() {
        value.unblockSerialization();
    }

    private static class BlockingStringLeaf extends StringLeaf {

        private static final long CLASS_ID = 0x9C829FF3B2284L;

        private CountDownLatch serializationLatch;

        public BlockingStringLeaf() {
            super("BlockingStringLeaf");
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

        @Override
        public long getClassId() {
            return CLASS_ID;
        }

        @Override
        public void serialize(final SerializableDataOutputStream out) throws IOException {
            if (serializationLatch != null) {
                abortAndThrowIfInterrupted(serializationLatch::await, "interrupted while waiting for latch");
            }
            super.serialize(out);
        }

        @Override
        public void deserialize(SerializableDataInputStream in, int version) throws IOException {
            super.deserialize(in, version);
        }
    }
}
