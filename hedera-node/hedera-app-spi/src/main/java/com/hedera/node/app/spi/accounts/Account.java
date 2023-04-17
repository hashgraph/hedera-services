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

package com.hedera.node.app.spi.accounts;

import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/** An Account entity represents a Hedera Account. */
public interface Account {
    long HBARS_TO_TINYBARS = 100_000_000L;
    /**
     * Account's account number
     *
     * @return account number
     */
    long accountNumber();

    /**
     * Return alias if the account has an alias. Otherwise returns {@code null}
     *
     * @return alias if exists
     */
    @Nullable
    Bytes alias();

    /**
     * Gets whether this is a "hollow" account. A hollow account is an account that was created
     * automatically as a result of a crypto transfer (or token transfer, or other transfer of
     * value) into a non-existent account via alias or virtual address.
     *
     * @return True if this is a hollow account
     */
    boolean isHollow();

    /**
     * The keys on the account. This may return a null if the key on account is
     * null. For e.g., for account 0.0.800 and Hollow accounts (as determined by {@link
     * #isHollow()})
     *
     * @return An optional key list
     */
    @Nullable
    HederaKey getKey();

    /**
     * Gets the expiration time of the account, in millis from the epoch.
     *
     * @return The expiry of the account in millis from the epoch.
     */
    long expiry();

    /**
     * Gets the hbar balance of the account. The balance will always be non-negative.
     *
     * @return The hbar balance (in hbar)
     */
    default long balanceInHbar() {
        return balanceInTinyBar() / HBARS_TO_TINYBARS;
    }

    /**
     * Gets the hbar balance of the account. The balance will always be non-negative.
     *
     * @return The hbar balance (in tinybar)
     */
    long balanceInTinyBar();

    /**
     * When the account reaches its expiration time, the account will be charged extend its
     * expiration date every this many seconds. If it doesn't have enough balance, it extends as
     * long as possible. If it is empty when it expires, then it is deleted.
     *
     * @return auto renew period seconds
     */
    long autoRenewSecs();

    /**
     * Memo for the account
     *
     * @return account's memo
     */
    @NonNull
    String memo();

    /**
     * Checks if the account is deleted
     *
     * @return true if it deleted, false otherwise
     */
    boolean isDeleted();

    /**
     * Checks if it is a smart-contract account
     *
     * @return true if it is a smart-contract, false otherwise
     */
    boolean isSmartContract();

    /**
     * Checks if the account should sign the transaction when receiving any funds
     *
     * @return true if it has receiverSigRequired set, false otherwise
     */
    boolean isReceiverSigRequired();

    /**
     * Number of NFTs owned by the account
     *
     * @return number of NFTs owned
     */
    long numberOfOwnedNfts();

    /**
     * The maximum number of tokens that the account can be implicitly associated with
     *
     * @return number of maximum auto associations
     */
    int maxAutoAssociations();

    /**
     * Number of token auto association slots used from the maximum available
     *
     * @return used auto association slots
     */
    int usedAutoAssociations();

    /**
     * Number of total tokens associated to this account
     *
     * @return number of total associations
     */
    int numAssociations();

    /**
     * Number of total positive fungible token balances on this account
     *
     * @return total positive token balances on this account
     */
    int numPositiveBalances();

    /**
     * The ethereum transaction nonce associated with this account.
     *
     * @return ethereum transaction nonce
     */
    long ethereumNonce();

    /**
     * The total of balance of all accounts staked to this account or contract.
     *
     * @return total stake
     */
    long stakedToMe();

    /**
     * The staking period during which either the staking settings for this account or contract
     * changed (such as starting staking or changing staked_node_id) or the most recent reward was
     * earned, whichever is later. If this account or contract is not currently staked to a node,
     * then this field is not set.
     *
     * @return stake period start for this account
     */
    long stakePeriodStart();

    /**
     * Get the num of [of shard.realm.num] node/account this account has staked its hbar to If the
     * returned value is negative it is staked to a node and node num is the absolute value of
     * (-stakedNum - 1). If the returned value is positive it is staked to an account and the
     * accountNum is stakedNum.
     *
     * @return num [of shard.realm.num] of node/account
     */
    long stakedNum();

    /**
     * If the account chose to not receive reward while staking to a node
     *
     * @return true, if account chose to not receive reward. False otherwise
     */
    boolean declineReward();

    /**
     * Total stake of the account at start of last rewarded period
     *
     * @return stake of the account at start of last rewarded period
     */
    long stakeAtStartOfLastRewardedPeriod();

    /**
     * Get the num [of shard.realm.num] of account to charge for auto-renewal of this account. If
     * not set, or set to an account with zero hbar balance, the account's own hbar balance will be
     * used to cover auto-renewal fees.
     *
     * @return auto-renew account number
     */
    long autoRenewAccountNumber();

    /**
     * Creates an AccountBuilder that clones all state in this instance, allowing the user to
     * override only the specific state that they choose to override.
     *
     * @return A non-null builder pre-initialized with all state in this instance.
     */
    @NonNull
    AccountBuilder copy();
}
