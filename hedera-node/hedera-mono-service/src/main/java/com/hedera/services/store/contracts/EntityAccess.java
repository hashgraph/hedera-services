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
package com.hedera.services.store.contracts;

import com.hedera.services.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.accounts.HederaAccountCustomizer;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;

public interface EntityAccess extends HederaEvmEntityAccess {
    /**
     * Provides a {@link WorldLedgers} whose {@link com.hedera.services.ledger.TransactionalLedger}
     * instances commit directly to the Hedera world state. Only makes sense to return
     * non-degenerate ledgers for a mutable {@link EntityAccess} implementation (though both mutable
     * and static entity access do require the alias "ledger").
     *
     * @return the world state ledgers if applicable
     */
    WorldLedgers worldLedgers();

    void startAccess();

    String currentManagedChangeSet();

    /* --- Account access --- */
    void customize(AccountID id, HederaAccountCustomizer customizer);

    boolean isUsable(Address address);

    /* --- Storage access --- */
    void recordNewKvUsageTo(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger);

    void putStorage(AccountID id, UInt256 key, UInt256 value);

    void flushStorage(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger);

    /* --- Bytecode access --- */
    void storeCode(AccountID id, Bytes code);
}
