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
package com.hedera.services.store.contracts.precompile.utils;

import com.hedera.services.store.contracts.WorldLedgers;
import org.hyperledger.besu.datatypes.Address;

@FunctionalInterface
public interface KeyActivationTest {
    /**
     * Returns whether a key implicit in the target address is active, given an idealized message
     * frame in which:
     *
     * <ul>
     *   <li>The {@code recipient} address is the account receiving the call operation; and,
     *   <li>The {@code contract} address is the account with the code being executed; and,
     *   <li>Any {@code ContractID} or {@code delegatable_contract_id} key that matches the {@code
     *       activeContract} address should be considered active (modulo whether the recipient and
     *       contract imply a delegate call).
     * </ul>
     *
     * <p>Note the target address might not imply an account key, but e.g. a token supply key.
     *
     * @param isDelegateCall a flag showing if the message represented by the active frame is
     *     invoked via {@code delegatecall}
     * @param target an address with an implicit key understood by this implementation
     * @param activeContract the contract address that can activate a contract or delegatable
     *     contract key
     * @param worldLedgers the worldLedgers representing current state
     * @return whether the implicit key has an active signature in this context
     */
    boolean apply(
            boolean isDelegateCall,
            Address target,
            Address activeContract,
            WorldLedgers worldLedgers);
}
