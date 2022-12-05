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
package com.hedera.node.app.service.token.entity;

import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Builds an account using a builder pattern */
public interface AccountBuilder {
    /**
     * Override the key specified on the account
     *
     * @param key account's key
     * @return builder object
     */
    @NonNull
    AccountBuilder key(@NonNull HederaKey key);

    /**
     * Sets expiration time in seconds on the account
     *
     * @param value expiration value
     * @return builder object
     */
    @NonNull
    AccountBuilder expiry(long value);

    /**
     * Sets the hbar balance of the account.
     *
     * @param value The hbar balance of the account (in tinybar). Must be non-negative or IAE.
     * @return builder object
     * @throws IllegalArgumentException if the value is less than 0 or greater than 50 billion.
     */
    @NonNull
    AccountBuilder balance(long value);

    /**
     * Sets memo on the account
     *
     * @param value memo
     * @return builder object
     */
    @NonNull
    AccountBuilder memo(String value);

    /**
     * Sets if account is deleted on the account
     *
     * @param value true account is deleted, false otherwise
     * @return builder object
     */
    @NonNull
    AccountBuilder deleted(boolean value);

    /**
     * Sets if account's signature is needed when receiving funds
     *
     * @param value true if account's signature is needed, false otherwise
     * @return builder object
     */
    @NonNull
    AccountBuilder receiverSigRequired(boolean value);

    /**
     * Sets number of NFTs owned by the account
     *
     * @param value number of NFTs owned by the account
     * @return builder object
     */
    @NonNull
    AccountBuilder numberOfOwnedNfts(long value);

    /**
     * Sets maximum number of tokens that the account can be implicitly associated with
     *
     * @param value maximum number of tokens that the account can be implicitly associated with
     * @return builder object
     */
    @NonNull
    AccountBuilder maxAutoAssociations(int value);

    /**
     * Sets number of token auto association slots used from the maximum available
     *
     * @param value number of token auto association slots used
     * @return builder object
     */
    @NonNull
    AccountBuilder usedAutoAssociations(int value);

    /**
     * Sets number of total tokens associated to this account
     *
     * @param value number of total tokens associated
     * @return builder object
     */
    @NonNull
    AccountBuilder numAssociations(int value);

    /**
     * Sets number of total positive fungible token balances on this account
     *
     * @param value number of total positive fungible token balances
     * @return builder object
     */
    @NonNull
    AccountBuilder numPositiveBalances(int value);

    /**
     * Sets ethereum transaction nonce associated with this account
     *
     * @param value ethereum transaction nonce
     * @return builder object
     */
    @NonNull
    AccountBuilder ethereumNonce(long value);

    /**
     * Sets total of balance of all accounts staked to this account or contract.
     *
     * @param value balance of all accounts staked to this account or contract
     * @return builder object
     */
    @NonNull
    AccountBuilder stakedToMe(long value);

    /**
     * Sets the staking period during which either the staking settings for this account or contract
     * changed
     *
     * @param value staking period
     * @return builder object
     */
    @NonNull
    AccountBuilder stakePeriodStart(long value);

    /**
     * Sets the num of [of shard.realm.num] node or account this account has staked its hbar to
     *
     * @param value node or account number
     * @return builder object
     */
    @NonNull
    AccountBuilder stakedNum(long value);

    /**
     * Sets if the account chose to not receive reward while staking to a node
     *
     * @param value true if the account chose to not receive reward, false otherwise
     * @return builder object
     */
    @NonNull
    AccountBuilder declineReward(boolean value);

    /**
     * Sets total stake of the account at start of last rewarded period
     *
     * @param value total stake of the account at start of last rewarded period
     * @return builder object
     */
    @NonNull
    AccountBuilder stakeAtStartOfLastRewardedPeriod(long value);

    /**
     * Sets the num [of shard.realm.num] of account to charge for auto-renewal of this account
     *
     * @param value auto-renew account's number
     * @return builder object
     */
    @NonNull
    AccountBuilder autoRenewAccountNumber(long value);

    /**
     * Sets the number of seconds the account's expiration is extended, when it reaches expiration
     *
     * @param value auto-renewal seconds
     * @return builder object
     */
    @NonNull
    AccountBuilder autoRenewSecs(long value);

    /**
     * Sets the accountNumber for the account
     *
     * @param value account's number
     * @return builder object
     */
    @NonNull
    AccountBuilder accountNumber(long value);
    /**
     * Sets the alias for the account
     *
     * @param value account's alias
     * @return builder object
     */
    @NonNull
    AccountBuilder alias(byte[] value);
    /**
     * Sets if the account is smart contract
     *
     * @param value true if the account is smart contract, false otherwise
     * @return builder object
     */
    @NonNull
    AccountBuilder isSmartContract(boolean value);

    /**
     * Builds and returns an account with the state specified in the builder
     *
     * @return A non-null reference to a **new** account. Two calls to this method return different
     *     instances.
     */
    @NonNull
    Account build();
}
