/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.infrastructure.selectors;

import java.util.Iterator;
import java.util.Optional;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class RandomSelector implements BiFunction<Set<String>, Set<String>, Optional<String>> {
    private static final Logger log = LogManager.getLogger(RandomSelector.class);

    SplittableRandom r = new SplittableRandom();

    private final Predicate<String> eligibility;

    public RandomSelector() {
        this(ignore -> true);
    }

    public RandomSelector(Predicate<String> eligibility) {
        this.eligibility = eligibility;
    }

    @Override
    public synchronized Optional<String> apply(Set<String> candidates, Set<String> ineligible) {
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        int n = r.nextInt(candidates.size());
        String candidate = null;
        try {
            Iterator<String> iterator = candidates.iterator();
            for (int i = 0; (i <= n) || (candidate == null); i++) {
                if (!iterator.hasNext()) {
                    break;
                }
                String nextCandidate = iterator.next();
                if (!ineligible.contains(nextCandidate) && eligibility.test(nextCandidate)) {
                    candidate = nextCandidate;
                }
            }
        } catch (Exception ignore) {
        }

        if (candidate == null || ineligible.contains(candidate) || !eligibility.test(candidate)) {
            return Optional.empty();
        }

        return Optional.of(candidate);
    }
}
