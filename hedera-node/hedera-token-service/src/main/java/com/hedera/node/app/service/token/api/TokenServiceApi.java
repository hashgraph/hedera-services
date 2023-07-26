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

package com.hedera.node.app.service.token.api;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.ContractNonceInfo;
import com.hedera.hapi.node.state.token.Account;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public interface TokenServiceApi {
    /**
     * Creates a new contract whose initial properties are customized by the given spec.
     *
     * @param idToCreate the id of the contract to create
     * @param spec the spec that should be used to customize the contract account
     */
    void createAndMaybeAliasContract(@NonNull ContractID idToCreate, @NonNull Consumer<Account.Builder> spec);

    /**
     * Deletes the contract with the given id.
     *
     * @param idToDelete the id of the contract to delete
     */
    void deleteAndMaybeUnaliasContract(@NonNull ContractID idToDelete);

    /**
     * Increments the nonce of the given contract.
     *
     * @param parentId the id of the contract whose nonce should be incremented
     */
    void incrementParentNonce(@NonNull ContractID parentId);

    /**
     * Increments the nonce of the given sender.
     *
     * @param senderId the id of the sender whose nonce should be incremented
     */
    void incrementSenderNonce(@NonNull AccountID senderId);

    /**
     * Transfers the given amount from the given sender to the given recipient.
     *
     * @param from the id of the sender
     * @param to the id of the recipient
     * @param amount the amount to transfer
     */
    void transferFromTo(@NonNull AccountID from, @NonNull AccountID to, long amount);

    /**
     * Returns a list of all the account ids that were modified by this {@link TokenServiceApi}.
     *
     * @return a list of all the account ids that were modified by this {@link TokenServiceApi}
     */
    Set<AccountID> modifiedAccountIds();

    /**
     * Returns a list of the contract nonces updated by this {@link TokenServiceApi}.
     *
     * @return a list of all the account ids that were modified by this {@link TokenServiceApi}
     */
    List<ContractNonceInfo> updatedContractNonces();
}
