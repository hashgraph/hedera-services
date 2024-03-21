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

package com.swirlds.state.spi.worfklows.record;

import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.SortedSet;

/**
 * A class that stores entities created during node startup, for the purpose of creating synthetic
 * records after startup
 */
public interface GenesisRecordsBuilder {
    /**
     * Tracks the system accounts created during node startup
     */
    void systemAccounts(@NonNull final SortedSet<Account> accounts);

    /**
     * Tracks the staking accounts created during node startup
     */
    void stakingAccounts(@NonNull final SortedSet<Account> accounts);

    /**
     * Tracks miscellaneous accounts created during node startup. These accounts are typically used for testing
     */
    void miscAccounts(@NonNull final SortedSet<Account> accounts);

    /**
     * Tracks the treasury clones created during node startup
     */
    void treasuryClones(@NonNull final SortedSet<Account> accounts);

    /**
     * Tracks the blocklist accounts created during node startup
     */
    void blocklistAccounts(@NonNull final SortedSet<Account> accounts);
}
