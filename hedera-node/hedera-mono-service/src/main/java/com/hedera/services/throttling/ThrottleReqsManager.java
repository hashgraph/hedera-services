/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.throttling;

import com.hedera.services.sysfiles.domain.throttling.ThrottleReqOpsScaleFactor;
import com.hedera.services.throttles.DeterministicThrottle;
import java.time.Instant;
import java.util.ArrayList;
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

    public boolean allReqsMetAt(
            Instant now, int nTransactions, ThrottleReqOpsScaleFactor scaleFactor) {
        return allVerboseReqsMetAt(now, nTransactions, scaleFactor);
    }

    private boolean allVerboseReqsMetAt(
            Instant now, int nTransactions, ThrottleReqOpsScaleFactor scaleFactor) {
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

    public List<DeterministicThrottle.UsageSnapshot> currentUsage() {
        List<DeterministicThrottle.UsageSnapshot> usages = new ArrayList<>();
        for (var req : allReqs) {
            usages.add(req.getLeft().usageSnapshot());
        }
        return usages;
    }

    List<DeterministicThrottle> managedThrottles() {
        return allReqs.stream().map(Pair::getLeft).toList();
    }

    String asReadableRequirements() {
        return "min{"
                + allReqs.stream().map(this::readable).collect(Collectors.joining(", "))
                + "}";
    }

    private String readable(Pair<DeterministicThrottle, Integer> req) {
        var throttle = req.getLeft();
        return approximateTps(req.getRight(), throttle.mtps()) + " tps (" + throttle.name() + ")";
    }

    private String approximateTps(int logicalTpsReq, long bucketMtps) {
        return String.format("%.2f", (1.0 * bucketMtps) / 1000.0 / logicalTpsReq);
    }
}
