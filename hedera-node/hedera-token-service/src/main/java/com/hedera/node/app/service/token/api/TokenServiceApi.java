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
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.util.List;
import java.util.Set;

/**
 * Defines mutations that can't be expressed as a {@link com.hedera.hapi.node.transaction.TransactionBody} dispatch.
 *
 * <p>Only exported to the contract service at this time, as it is the only service that currently needs such a thing.
 * If, for example, we extract a {@code StakingService}, this API would likely need to expand.
 */
public interface TokenServiceApi {
    /**
     * Validates the given staking election relative to the given account store, network info, and staking config.
     *
     * @param isStakingEnabled       if staking is enabled
     * @param hasDeclineRewardChange if the transaction body has decline reward field to be updated
     * @param stakedIdKind           staked id kind (account or node)
     * @param stakedAccountIdInOp    staked account id
     * @param stakedNodeIdInOp       staked node id
     * @param accountStore           readable account store
     * @throws HandleException if the staking election is invalid
     */
    void assertValidStakingElection(
            boolean isStakingEnabled,
            boolean hasDeclineRewardChange,
            @NonNull String stakedIdKind,
            @Nullable AccountID stakedAccountIdInOp,
            @Nullable Long stakedNodeIdInOp,
            @NonNull ReadableAccountStore accountStore,
            @NonNull NetworkInfo networkInfo);

    /**
     * Marks an account as a contract.
     *
     */
    void markAsContract(@NonNull AccountID justCreated);

    /**
     * Deletes the contract with the given id.
     *
     * @param idToDelete the id of the contract to delete
     */
    void deleteAndMaybeUnaliasContract(@NonNull AccountID idToDelete);

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
     * Sets the nonce of the given account.
     *
     * @param accountId the id of the account whose nonce should set
     * @param nonce the nonce to set
     */
    void setNonce(@NonNull AccountID accountId, long nonce);

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
     * @return the account ids that were modified by this {@link TokenServiceApi}
     */
    Set<AccountID> modifiedAccountIds();

    /**
     * Returns a list of the contract nonces updated by this {@link TokenServiceApi}.
     *
     * @return a list of all the account ids that were modified by this {@link TokenServiceApi}
     */
    List<ContractNonceInfo> updatedContractNonces();
}
