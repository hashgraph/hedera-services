/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.consistency;

import static com.swirlds.logging.legacy.LogMarker.STARTUP;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Consistency Testing Tool
 */
@ConstructableIgnored
public class ConsistencyTestingToolState extends PlatformMerkleStateRoot {
    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);
    private static final long CLASS_ID = 0xda03bb07eb897d82L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Nodes at indices 0, 1, and 2 are used by the PlatformState, RosterMap, and RosterState.
    private static final int STATE_LONG_INDEX = 3;
    private static final int ROUND_HANDLED_INDEX = 4;

    /**
     * The true "state" of this app. This long value is updated with every transaction, and with every round.
     * <p>
     * Affects the hash of this node.
     */
    private long stateLong = 0;

    /**
     * The number of rounds handled by this app. Is incremented each time
     * {@link ConsistencyTestingToolStateLifecycles#onHandleConsensusRound(Round, ConsistencyTestingToolState)} is called. Note that this may not actually equal the round
     * number, since we don't call {@link ConsistencyTestingToolStateLifecycles#onHandleConsensusRound(Round, ConsistencyTestingToolState)} for rounds with no events.
     *
     * <p>
     * Affects the hash of this node.
     */
    private long roundsHandled = 0;

    /**
     * Constructor
     */
    public ConsistencyTestingToolState(@NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(versionFactory);
        logger.info(STARTUP.getMarker(), "New State Constructed.");
    }

    /**
     * Initialize the state
     */
    void initState() {
        final StringLeaf stateLongLeaf = getChild(STATE_LONG_INDEX);
        if (stateLongLeaf != null && stateLongLeaf.getLabel() != null) {
            this.stateLong = Long.parseLong(stateLongLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with state long {}.", stateLong);
        }
        final StringLeaf roundsHandledLeaf = getChild(ROUND_HANDLED_INDEX);
        if (roundsHandledLeaf != null && roundsHandledLeaf.getLabel() != null) {
            this.roundsHandled = Long.parseLong(roundsHandledLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with {} rounds handled.", roundsHandled);
        }
    }

    /**
     * @return the number of rounds handled
     */
    long getRoundsHandled() {
        return roundsHandled;
    }

    /**
     * Increment the number of rounds handled
     */
    void incrementRoundsHandled() {
        roundsHandled++;
        setChild(ROUND_HANDLED_INDEX, new StringLeaf(Long.toString(roundsHandled)));
    }

    /**
     * @return the state represented by a long
     */
    long getStateLong() {
        return stateLong;
    }

    /**
     * Sets the state
     * @param stateLong state represtented by a long
     */
    void setStateLong(final long stateLong) {
        this.stateLong = stateLong;
        setChild(STATE_LONG_INDEX, new StringLeaf(Long.toString(stateLong)));
    }

    /**
     * Copy constructor
     *
     * @param that the state to copy
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(Objects.requireNonNull(that));
        this.stateLong = that.stateLong;
        this.roundsHandled = that.roundsHandled;
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
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized ConsistencyTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new ConsistencyTestingToolState(this);
    }
}
