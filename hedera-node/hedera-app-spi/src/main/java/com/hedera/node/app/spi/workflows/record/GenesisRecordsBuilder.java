/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.spi.workflows.record;

import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoCreateTransactionBody;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;

/**
 * A class that stores entities created during node startup, for the purpose of creating synthetic
 * records after startup
 */
public interface GenesisRecordsBuilder {
    /**
     * Tracks the system accounts created during node startup
     */
    void systemAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts);

    /**
     * Tracks the staking accounts created during node startup
     */
    void stakingAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts);

    /**
     * Tracks miscellaneous accounts created during node startup. These accounts are typically used for testing
     */
    void miscAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts);

    /**
     * Tracks the treasury clones created during node startup
     */
    void treasuryClones(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts);

    /**
     * Tracks the blocklist accounts created during node startup
     */
    void blocklistAccounts(@NonNull final Map<Account, CryptoCreateTransactionBody.Builder> accounts);
}
