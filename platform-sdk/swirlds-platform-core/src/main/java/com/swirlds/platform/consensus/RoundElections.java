/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.consensus;

import static com.swirlds.logging.legacy.LogMarker.CONSENSUS_VOTING;

import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.IntReference;
import com.swirlds.platform.Utilities;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.MinimumJudgeInfo;
import com.swirlds.platform.system.events.EventConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A round whose witnesses are currently having their fame voted on in elections. This class tracks the witnesses and
 * their decided status.
 */
public class RoundElections {
    private static final Logger logger = LogManager.getLogger(RoundElections.class);
    /** the round number of witnesses we are voting on */
    private long round = ConsensusConstants.ROUND_FIRST;
    /** number of known witnesses in this round with unknown fame */
    private final IntReference numUnknownFame = new IntReference(0);
    /**
     * these witnesses are the first event in this round by each member (if a member forks, it could have multiple
     * witnesses in a single round)
     */
    private final List<CandidateWitness> elections = new ArrayList<>();
    /** the minimum generation of all the judges, this is only set once the judges are found */
    private long minGeneration = EventConstants.GENERATION_UNDEFINED;

    /**
     * @return the round number of witnesses we are voting on
     */
    public long getRound() {
        return round;
    }

    /**
     * Set the round number of witnesses we are voting on
     *
     * @param round the round to set to
     */
    public void setRound(final long round) {
        if (this.round != ConsensusConstants.ROUND_FIRST) {
            throw new IllegalStateException(
                    "We should not set the election round on an instance that has not been reset");
        }
        this.round = round;
    }

    /**
     * A new witness is being added to the current election round
     *
     * @param witness the witness being added
     */
    public void addWitness(@NonNull final EventImpl witness) {
        if (logger.isDebugEnabled(CONSENSUS_VOTING.getMarker())) {
            logger.debug(
                    CONSENSUS_VOTING.getMarker(),
                    "Adding witness for election {}",
                    witness.getBaseEvent().getDescriptor());
        }
        numUnknownFame.increment();
        elections.add(new CandidateWitness(witness, numUnknownFame, elections.size()));
    }

    /**
     * @return the number of witnesses in this round being voted on
     */
    public int numElections() {
        return elections.size();
    }

    /**
     * @return an iterator of all undecided witnesses
     */
    public @NonNull Iterator<CandidateWitness> undecidedWitnesses() {
        return elections.stream().filter(CandidateWitness::isNotDecided).iterator();
    }

    /**
     * @return true if fame has been decided for all witnesses in this round. A round must have witnesses, so if no
     * witnesses have been added to this round yet, it cannot be decided, thus it will return false.
     */
    public boolean isDecided() {
        return numUnknownFame.equalsInt(0) && !elections.isEmpty();
    }

    /**
     * @return the minimum generation of all the judges(unique famous witnesses) in this round
     */
    public long getMinGeneration() {
        if (minGeneration == EventConstants.GENERATION_UNDEFINED) {
            throw new IllegalStateException("Cannot provide the minimum generation until all judges are found");
        }
        return minGeneration;
    }

    /**
     * @return create a {@link MinimumJudgeInfo} instance for this round
     */
    public @NonNull MinimumJudgeInfo createMinimumJudgeInfo() {
        return new MinimumJudgeInfo(round, getMinGeneration());
    }

    /**
     * Finds all judges in this round. This must be called only once all elections have been decided.
     *
     * @return all the judges for this round
     */
    public @NonNull List<EventImpl> findAllJudges() {
        if (!isDecided()) {
            throw new IllegalStateException("Cannot find all judges if the round has not been decided yet");
        }
        // This map is keyed by node id, and ensures that each creator has only a single famous witness even if that
        // creator branched
        final Map<NodeId, EventImpl> uniqueFamous = new HashMap<>();
        for (final CandidateWitness election : elections) {
            if (!election.isFamous()) {
                continue;
            }
            uniqueFamous.merge(
                    election.getWitness().getCreatorId(), election.getWitness(), RoundElections::uniqueFamous);
        }
        final List<EventImpl> allJudges = new ArrayList<>(uniqueFamous.values());
        allJudges.sort(Comparator.comparingLong(e -> e.getCreatorId().id()));
        minGeneration = allJudges.stream()
                .mapToLong(EventImpl::getGeneration)
                .min()
                .orElse(EventConstants.GENERATION_UNDEFINED);
        allJudges.forEach(EventImpl::setJudgeTrue);

        return allJudges;
    }

    /**
     * If a creator has more than one famous witnesses in a round (because he forked), pick which one will be the
     * judge.
     *
     * @param e1 famous witness 1
     * @param e2 famous witness 2
     * @return the witness which should be the judge
     */
    private static @NonNull EventImpl uniqueFamous(@NonNull final EventImpl e1, @Nullable final EventImpl e2) {
        if (e2 == null) {
            return e1;
        }
        // if this creator forked, then the judge is the "unique" famous witness, which is the one
        // with minimum hash
        // (where "minimum" is the lexicographically-least signed byte array)
        if (Utilities.arrayCompare(e1.getBaseHash().getBytes(), e2.getBaseHash().getBytes()) < 0) {
            return e1;
        }
        return e2;
    }

    /** End this election and start the election for the next round */
    public void startNextElection() {
        round++;
        numUnknownFame.set(0);
        elections.clear();
        minGeneration = EventConstants.GENERATION_UNDEFINED;
    }

    /** Reset this instance to its initial state */
    public void reset() {
        startNextElection();
        round = ConsensusConstants.ROUND_FIRST;
    }
}
