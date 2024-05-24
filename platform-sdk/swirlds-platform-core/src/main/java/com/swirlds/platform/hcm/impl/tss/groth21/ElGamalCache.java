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

package com.swirlds.platform.hcm.impl.tss.groth21;

import com.swirlds.platform.hcm.api.pairings.FieldElement;
import com.swirlds.platform.hcm.api.pairings.Group;
import com.swirlds.platform.hcm.api.pairings.GroupElement;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;

/**
 * A cache of ElGamal values.
 * <p>
 * TODO: improve this doc.
 *
 * @param cacheMap the cache map
 */
public record ElGamalCache(@NonNull Map<GroupElement, FieldElement> cacheMap) {
    /**
     * Generate a cache of ElGamal values.
     *
     * @param cacheGroup the group of the cache
     * @param cacheSize  the size of the cache
     * @return the cache
     */
    public static ElGamalCache create(@NonNull final Group cacheGroup, final int cacheSize) {
        final Map<GroupElement, FieldElement> cacheMap = new HashMap<>(cacheSize);
        for (int i = 0; i < cacheSize; i++) {
            final FieldElement indexElement = cacheGroup.getPairing().getField().elementFromLong(i);
            final GroupElement candidate = cacheGroup.getGenerator().power(indexElement);
            cacheMap.put(candidate, indexElement);
        }

        return new ElGamalCache(cacheMap);
    }
}
