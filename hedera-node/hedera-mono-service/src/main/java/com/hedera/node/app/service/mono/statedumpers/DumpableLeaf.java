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

package com.hedera.node.app.service.mono.statedumpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum DumpableLeaf {
    /**
     * Entity ids, block info, and running hashes
     */
    BLOCK_METADATA,
    /**
     * Throttle usage snapshots and congestion level starts
     */
    THROTTLE_METADATA,
    /**
     * Topics
     */
    TOPICS,
    /**
     * Contract bytecode
     */
    BYTECODE,
    /**
     * (FUTURE) Contract storage
     */
    STORAGE,
    /**
     * (FUTURE) Midnight rates
     */
    MIDNIGHT_RATES,
    /**
     * Files
     */
    FILES,
    /**
     * (FUTURE) Upgrade zip file
     */
    UPGRADE_DATA_FILE_150,
    /**
     * (FUTURE) Hash of Upgrade zip file
     */
    UPGRADE_FILE_HASH,
    /**
     * (FUTURE) Freeze time
     */
    FREEZE_TIME,
    /**
     * Last 180 seconds of handled transactions
     */
    TRANSACTION_RECORD_QUEUE,
    /**
     * Schedule service state
     */
    SCHEDULED_TRANSACTIONS,
    /**
     * Accounts
     */
    ACCOUNTS,
    /**
     * (FUTURE) Account aliases
     */
    ALIASES,
    /**
     * Unique tokens
     */
    NFTS,
    /**
     * Node staking information
     */
    STAKING_INFOS,
    /**
     * Pending rewards and other network staking metadata
     */
    STAKING_NETWORK_METADATA,
    /**
     * Tokens
     */
    TOKENS,
    /**
     * Token associations
     */
    TOKEN_RELS;

    /**
     * Returns the checkpoints selected by the {@code dumpCheckpoints}
     * system property, if set.
     *
     * @return the selected checkpoints, or an empty set if none are selected
     */
    public static Set<DumpableLeaf> selectedLeaves() {
        if (leaves == null) {
            final var literalSelection =
                    Optional.ofNullable(System.getProperty("leavesToDump")).orElse("");
            leaves = literalSelection.isEmpty()
                    ? Collections.emptySet()
                    : Arrays.stream(literalSelection.split(","))
                            .map(DumpableLeaf::valueOf)
                            .collect(Collectors.toCollection(() -> EnumSet.noneOf(DumpableLeaf.class)));
            System.out.println("Dumping leaves: " + leaves);
        }
        return leaves;
    }

    private static Set<DumpableLeaf> leaves = null;
}
