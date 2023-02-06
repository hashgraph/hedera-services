/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts;

import com.hedera.node.app.service.evm.store.contracts.HederaEvmEntityAccess;
import com.hedera.node.app.service.mono.ledger.TransactionalLedger;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.properties.AccountProperty;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.tuweni.bytes.Bytes;

public interface EntityAccess extends HederaEvmEntityAccess {
    /**
     * Provides a {@link WorldLedgers} whose {@link TransactionalLedger} instances commit directly
     * to the Hedera world state. Only makes sense to return non-degenerate ledgers for a mutable
     * {@link EntityAccess} implementation (though both mutable and static entity access do require
     * the alias "ledger").
     *
     * @return the world state ledgers if applicable
     */
    WorldLedgers worldLedgers();

    void startAccess();

    String currentManagedChangeSet();

    /* --- Account access --- */
    void customize(AccountID id, HederaAccountCustomizer customizer);

    /* --- Storage access --- */
    void recordNewKvUsageTo(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger);

    void putStorage(AccountID id, Bytes key, Bytes value);

    void flushStorage(
            TransactionalLedger<AccountID, AccountProperty, HederaAccount> accountsLedger);

    /* --- Bytecode access --- */
    void storeCode(AccountID id, Bytes code);
}
