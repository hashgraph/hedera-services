/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

import com.swirlds.common.constructable.ClassConstructorPair;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

/**
 * A test implementation of {@link PlatformMerkleStateRoot} state for SignedStateManager unit tests.
 * Node that some of the {@link PlatformMerkleStateRoot} methods are intentionally not implemented. If a test needs these methods,
 * {@link MerkleStateRoot} should be used instead.
 */
public class BlockingState extends PlatformMerkleStateRoot {

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
    private TestPlatformStateFacade platformStateFacade;

    public BlockingState() {
        this(new TestPlatformStateFacade(v -> new BasicSoftwareVersion(v.major())));
    }

    /**
     * Constructs a new instance of {@link BlockingState}.
     */
    public BlockingState(TestPlatformStateFacade platformStateFacade) {
        super(version -> new BasicSoftwareVersion(version.major()));
        this.platformStateFacade = platformStateFacade;
        value = new BlockingStringLeaf();
        setChild(1, value);
    }

    private BlockingState(final BlockingState that) {
        super(that);
        this.value = that.value;
        setChild(1, value);
    }

    /**
     * {@inheritDoc}
     */
    @NonNull
    @Override
    public BlockingState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new BlockingState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof final BlockingState that)) {
            return false;
        }
        return Objects.equals(platformStateFacade.platformStateOf(this), platformStateFacade.platformStateOf(that));
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
