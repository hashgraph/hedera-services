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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.inventory;

import static com.hedera.services.bdd.spec.keys.KeyShape.randomly;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

public class KeyInventoryCreation {
    public static final int DEFAULT_NUM_KEYS = 10;
    public static final int DEFAULT_MAX_DEPTH = 3;
    public static final int DEFAULT_MAX_LIST_SIZE = 3;
    public static final int DEFAULT_MAX_THRESHOLD_SIZE = 7;
    public static final double DEFAULT_LIST_PROB = 0.2;
    public static final double DEFAULT_SIMPLE_PROB = 0.5;
    public static final double DEFAULT_THRESHOLD_PROB = 0.3;

    private Random r = new Random();

    private int numKeys = DEFAULT_NUM_KEYS;
    private int maxDepth = DEFAULT_MAX_DEPTH;
    private int maxListSize = DEFAULT_MAX_LIST_SIZE;
    private int maxThresholdSize = DEFAULT_MAX_THRESHOLD_SIZE;
    private double listProb = DEFAULT_LIST_PROB;
    private double simpleProb = DEFAULT_SIMPLE_PROB;
    private double thresholdProb = DEFAULT_THRESHOLD_PROB;

    public HapiSpecOperation[] creationOps() {
        return IntStream.range(0, numKeys)
                .mapToObj(i -> newKeyNamed("randKey" + i)
                        .shape(randomly(maxDepth, this::someListSize, this::someType, this::someThresholdSizes)))
                .toArray(HapiSpecOperation[]::new);
    }

    public KeyFactory.KeyType someType() {
        double t = r.nextDouble();
        if (t < listProb) {
            return KeyFactory.KeyType.LIST;
        } else if (t < (listProb + simpleProb)) {
            return KeyFactory.KeyType.SIMPLE;
        } else if (t < (listProb + simpleProb + thresholdProb)) {
            return KeyFactory.KeyType.THRESHOLD;
        } else {
            throw new IllegalStateException("Underdetermined key type!");
        }
    }

    public int someListSize() {
        return r.nextInt(maxListSize) + 1;
    }

    public Map.Entry<Integer, Integer> someThresholdSizes() {
        int N = r.nextInt(maxThresholdSize) + 1;
        int M = r.nextInt(N) + 1;
        return new AbstractMap.SimpleEntry<>(M, N);
    }
}
