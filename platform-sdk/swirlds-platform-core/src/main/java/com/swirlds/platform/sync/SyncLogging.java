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

package com.swirlds.platform.sync;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.EventStrings;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility routines to generate formatted log string for sync-related variables.
 */
public final class SyncLogging {
    public static final int BRIEF_HASH_LENGTH = 4;

    /**
     * This type is not constructable
     */
    private SyncLogging() {}

    public static String toShortShadows(Collection<ShadowEvent> shadows) {
        if (shadows == null) {
            return "null";
        }
        return shadows.stream()
                .map(s -> EventStrings.toShortString(s.getEvent()))
                .collect(Collectors.joining(","));
    }

    public static String toShortHashes(List<Hash> hashes) {
        if (hashes == null) {
            return "null";
        }
        return hashes.stream()
                .map(h -> CommonUtils.hex(h.getValue(), BRIEF_HASH_LENGTH))
                .collect(Collectors.joining(","));
    }

    public static String toShortBooleans(List<Boolean> booleans) {
        if (booleans == null) {
            return "null";
        }
        return booleans.stream().map(b -> Boolean.TRUE.equals(b) ? "T" : "F").collect(Collectors.joining(","));
    }
}
