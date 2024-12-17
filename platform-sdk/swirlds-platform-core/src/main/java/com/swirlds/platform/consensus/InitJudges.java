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

package com.swirlds.platform.consensus;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.internal.EventImpl;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Used when initializing consensus from a set of judges. Consensus starts without any events and
 * only a set of judge hashes from the last decided round. Once a judge is found, its round created
 * is set and its hash is removed from the list.
 */
public final class InitJudges {
    /**
     * the roundCreated to set for a new event, if its hash is here (used during restart and
     * reconnect)
     */
    private final long round;

    private final Set<Hash> judgeHashes;
    /**
     * all judges in round hashRound that have been added so far (still need numInitJudgesMissing
     * more)
     */
    private final List<EventImpl> judges;

    /**
     * Create a new instance that handles init judges
     *
     * @param round the round created of the init judges
     * @param judgeHashes the hashes of the init judges
     */
    public InitJudges(final long round, @NonNull final Set<Hash> judgeHashes) {
        this.round = round;
        this.judgeHashes = judgeHashes;
        this.judges = new ArrayList<>();
    }

    /**
     * @return the round created of the init judges
     */
    public long getRound() {
        return round;
    }

    /**
     * Checks if this hash matches one of the init judges
     *
     * @param hash the hash to check
     * @return true if this hash belongs to one of the init judges
     */
    public boolean isInitJudge(@NonNull final Hash hash) {
        return judgeHashes.contains(hash);
    }

    /**
     * Process an init judge. This should only be called if {@link #isInitJudge(Hash)} returns true
     *
     * @param judge the judge
     */
    public void judgeFound(@NonNull final EventImpl judge) {
        judges.add(judge);
        judgeHashes.remove(judge.getBaseHash());
        judge.setRoundCreated(getRound());
        judge.setWitness(true);
        judge.setFamous(true);
        judge.setFameDecided(true);
        judge.setJudgeTrue();
    }

    /**
     * @return true if all the init judges have been found
     */
    public boolean allJudgesFound() {
        return judgeHashes.isEmpty();
    }

    /**
     * @return the number of init judges still not found
     */
    public int numMissingJudges() {
        return judgeHashes.size();
    }

    /**
     * @return a list of all init judges that have been found
     */
    public @NonNull List<EventImpl> getJudges() {
        return judges;
    }
}
