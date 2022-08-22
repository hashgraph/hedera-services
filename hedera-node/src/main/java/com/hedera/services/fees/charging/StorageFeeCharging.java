/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.charging;

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.store.contracts.KvUsageInfo;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.Map;

/** Defines a type able to charge storage rent due at the end of a contract transaction. */
public interface StorageFeeCharging {
    /**
     * Given the number of key/value pairs in state at the beginning of a transaction, and the new
     * key/value usage for each contract number that changed storage in the transaction, charges the
     * appropriate auto-renew accounts and/or contracts rent using the given {@code accounts}
     * ledger.
     *
     * <p>If the property {@code contracts.itemizeStorageFees} is true, the rent charges will be
     * itemized in a "following" child record whose memo is {@code "Contract storage fees"}.
     *
     * @param numTotalKvPairs the total key/value pairs in state
     * @param newUsageInfos the storage usage deltas in a transaction
     * @param accounts the ledger to use for charging rent
     */
    void chargeStorageRent(
            long numTotalKvPairs,
            Map<Long, KvUsageInfo> newUsageInfos,
            TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accounts);
}
