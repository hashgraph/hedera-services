/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.store.contracts.precompile.impl.sigs;

import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.store.contracts.WorldLedgers;
import com.hedera.node.app.service.mono.store.contracts.precompile.utils.KeyActivationTest;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

@FunctionalInterface
public interface KeyValidator {
    /**
     * Checks if an implicit key for the target address is active in the given context. Note the
     * address may represent any entity type; and the key may implicitly be of any sort (account
     * key, admin key, supply key, etc.)
     *
     * @param frame the current EVM frame
     * @param target the address of the entity with the key
     * @param activationTest a strategy for determining if the implicit key is active
     * @param ledgers the current updater's world ledgers
     * @param aliases the current updater's aliases
     * @return whether the implicit key is active
     */
    boolean validateKey(
            MessageFrame frame,
            Address target,
            KeyActivationTest activationTest,
            WorldLedgers ledgers,
            ContractAliases aliases);
}
