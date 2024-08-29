/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.turtle.runner;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.PlatformStateAccessor;
import com.swirlds.platform.state.State;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SwirldState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * A simple testing application intended for use with TURTLE.
 * <pre>
 *   _______    ֥  ֖       ֥  ֖    _______
 * 〈 Tᴜʀᴛʟᴇ ᐳ﹙⚬◡°﹚   ﹙°◡⚬﹚ᐸ ᴇʟᴛʀᴜT 〉
 *   ﹉∏﹉∏﹉                   ﹉∏﹉∏﹉
 * </pre>
 */
public class TurtleTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {

    private static final long CLASS_ID = 0xa49b3822a4136ac6L;

    private static final class ClassVersion {

        public static final int ORIGINAL = 1;
    }

    private long state;

    /**
     * Zero arg constructor needed for constructable registry.
     */
    public TurtleTestingToolState() {}

    /**
     * Copy constructor.
     *
     * @param that the object to copy
     */
    private TurtleTestingToolState(@NonNull final TurtleTestingToolState that) {
        this.state = that.state;
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
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(@NonNull final Round round, @NonNull final PlatformStateAccessor platformState) {
        state = NonCryptographicHashing.hash64(
                state,
                round.getRoundNum(),
                round.getConsensusTimestamp().getNano(),
                round.getConsensusTimestamp().getEpochSecond());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TurtleTestingToolState copy() {
        return new TurtleTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeLong(state);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        state = in.readLong();
    }

    /**
     * Creates a merkle node to act as a state tree root.
     *
     * @return merkle tree root
     */
    @NonNull
    public static MerkleRoot getStateRootNode() {
        final TurtleTestingToolState turtleState = new TurtleTestingToolState();
        final State root = new State();
        root.setSwirldState(turtleState);
        return root;
    }
}
