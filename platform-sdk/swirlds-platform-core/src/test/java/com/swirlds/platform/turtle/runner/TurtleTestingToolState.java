// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.turtle.runner;

import static com.swirlds.platform.test.fixtures.state.FakeMerkleStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.*;
import com.swirlds.platform.system.BasicSoftwareVersion;
import com.swirlds.platform.system.Round;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.function.Consumer;

/**
 * A simple testing application intended for use with TURTLE.
 * <pre>
 *   _______    ֥  ֖       ֥  ֖    _______
 * 〈 Tᴜʀᴛʟᴇ ᐳ﹙⚬◡°﹚   ﹙°◡⚬﹚ᐸ ᴇʟᴛʀᴜT 〉
 *   ﹉∏﹉∏﹉                   ﹉∏﹉∏﹉
 * </pre>
 */
public class TurtleTestingToolState extends PlatformMerkleStateRoot {

    private static final long CLASS_ID = 0xa49b3822a4136ac6L;

    private static final class ClassVersion {

        public static final int ORIGINAL = 1;
    }

    private long state;

    public TurtleTestingToolState() {
        super(FAKE_MERKLE_STATE_LIFECYCLES, version -> new BasicSoftwareVersion(1));
    }

    /**
     * Copy constructor.
     *
     * @param from the object to copy
     */
    private TurtleTestingToolState(@NonNull final TurtleTestingToolState from) {
        super(from);
        this.state = from.state;
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
    public void handleConsensusRound(
            @NonNull final Round round,
            @NonNull final PlatformStateModifier platformState,
            @NonNull
                    final Consumer<List<ScopedSystemTransaction<StateSignatureTransaction>>>
                            stateSignatureTransactions) {
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
        throwIfImmutable();
        setImmutable(true);
        return new TurtleTestingToolState(this);
    }

    /**
     * Creates a merkle node to act as a state tree root.
     *
     * @return merkle tree root
     */
    @NonNull
    public static PlatformMerkleStateRoot getStateRootNode() {
        final PlatformMerkleStateRoot state = new TurtleTestingToolState();
        FAKE_MERKLE_STATE_LIFECYCLES.initPlatformState(state);
        return state;
    }
}
