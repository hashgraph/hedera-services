/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.throttle;

import com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ScaleFactor;
import com.hedera.node.app.hapi.utils.throttles.BucketThrottle;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class ThrottleReqsManager {
    private final boolean[] passedReq;
    private final List<Pair<DeterministicThrottle, Integer>> allReqs;

    public ThrottleReqsManager(List<Pair<DeterministicThrottle, Integer>> allReqs) {
        this.allReqs = allReqs;
        passedReq = new boolean[allReqs.size()];
    }

    public boolean allReqsMetAt(Instant now) {
        return allVerboseReqsMetAt(now, 0, null);
    }

    public boolean allReqsMetAt(Instant now, int nTransactions, ScaleFactor scaleFactor) {
        return allVerboseReqsMetAt(now, nTransactions, scaleFactor);
    }

    /**
     * Given a number of logical transactions that had their requirements satisfied by this manager
     * at an earlier time, undoes the claimed capacity for those transactions.
     *
     * <p>We need this to support "reclaiming" capacity in the frontend throttles after an expensive
     * operation (like a {@link com.hedera.hapi.node.base.HederaFunctionality#CRYPTO_TRANSFER} that
     * does an auto-creation):
     * <ol>
     *     <li>Passes the frontend throttle check, using capacity in the buckets there; and,</li>
     *     <li>Fails at consensus, thus <I>not</I> using network capacity for the expensive operation in the end.</li>
     * </ol>
     *
     * @param nTransactions the number of transactions to undo
     */
    public void undoClaimedReqsFor(int nTransactions) {
        for (int i = 0; i < passedReq.length; i++) {
            final var req = allReqs.get(i);
            final var opsRequired = req.getRight();
            final var bucket = req.getLeft();
            bucket.leakCapacity(nTransactions * opsRequired * BucketThrottle.capacityUnitsPerTxn());
        }
    }

    private boolean allVerboseReqsMetAt(Instant now, int nTransactions, ScaleFactor scaleFactor) {
        var allPassed = true;
        for (int i = 0; i < passedReq.length; i++) {
            var req = allReqs.get(i);
            var opsRequired = req.getRight();
            if (scaleFactor != null) {
                opsRequired = scaleFactor.scaling(nTransactions * opsRequired);
            }
            passedReq[i] = req.getLeft().allow(opsRequired, now);
            allPassed &= passedReq[i];
        }

        return allPassed;
    }

    public List<DeterministicThrottle> managedThrottles() {
        return allReqs.stream().map(Pair::getLeft).toList();
    }

    public String asReadableRequirements() {
        return "min{" + allReqs.stream().map(this::readable).collect(Collectors.joining(", ")) + "}";
    }

    private String readable(Pair<DeterministicThrottle, Integer> req) {
        var throttle = req.getLeft();
        return approximateTps(req.getRight(), throttle.mtps()) + " tps (" + throttle.name() + ")";
    }

    private String approximateTps(int logicalTpsReq, long bucketMtps) {
        return String.format("%.2f", (1.0 * bucketMtps) / 1000.0 / logicalTpsReq);
    }
}
