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

package com.hedera.node.app.service.token.impl.entity;

import static com.hedera.node.app.service.token.entity.Account.HBARS_TO_TINYBARS;

import com.hedera.node.app.service.token.entity.Account;
import com.hedera.node.app.service.token.entity.AccountBuilder;
import com.hedera.node.app.spi.key.HederaKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.Optional;

/**
 * An implementation of {@link AccountBuilder} for building Account instances. This class is
 * <strong>not</strong> exported from the module. FUTURE: Should be moved to token-service-impl
 * module
 */
public class AccountBuilderImpl implements AccountBuilder {
    // These fields are the ones that can be set in the builder
    // FUTURE: Replace the empty KeyList we use for 0.0.800 and hollow
    // accounts with null
    private HederaKey key;
    private long expiry;
    private long balance;
    private String memo;
    private boolean deleted;
    private boolean receiverSigRequired;
    private long numberOfOwnedNfts;
    private int maxAutoAssociations;
    private int usedAutoAssociations;
    private int numAssociations;
    private int numPositiveBalances;
    private long ethereumNonce;
    private long stakedToMe;
    private long stakePeriodStart;
    private long stakedNum;
    private boolean declineReward;
    private long stakeAtStartOfLastRewardedPeriod;
    private long autoRenewAccountNumber;
    private long autoRenewSecs;
    private long accountNumber;
    private Optional<byte[]> alias;
    private boolean isSmartContract;

    /**
     * Create a builder for creating {@link Account}s, using the given copy as the basis for all
     * settings that are not overridden.
     *
     * @param copyOf The instance to copy
     */
    public AccountBuilderImpl(@NonNull Account copyOf) {
        Objects.requireNonNull(copyOf);
        this.key = copyOf.getKey().orElse(null);
        this.expiry = copyOf.expiry();
        this.balance = copyOf.balanceInTinyBar();
        this.memo = copyOf.memo();
        this.deleted = copyOf.isDeleted();
        this.receiverSigRequired = copyOf.isReceiverSigRequired();
        this.numberOfOwnedNfts = copyOf.numberOfOwnedNfts();
        this.maxAutoAssociations = copyOf.maxAutoAssociations();
        this.usedAutoAssociations = copyOf.usedAutoAssociations();
        this.numAssociations = copyOf.numAssociations();
        this.numPositiveBalances = copyOf.numPositiveBalances();
        this.ethereumNonce = copyOf.ethereumNonce();
        this.stakedToMe = copyOf.stakedToMe();
        this.stakePeriodStart = copyOf.stakePeriodStart();
        this.stakedNum = copyOf.stakedNum();
        this.declineReward = copyOf.declineReward();
        this.stakeAtStartOfLastRewardedPeriod = copyOf.stakeAtStartOfLastRewardedPeriod();
        this.autoRenewAccountNumber = copyOf.autoRenewAccountNumber();
        this.autoRenewSecs = copyOf.autoRenewSecs();
        this.accountNumber = copyOf.accountNumber();
        this.alias = copyOf.alias();
        this.isSmartContract = copyOf.isSmartContract();
    }

    public AccountBuilderImpl() {
        alias = Optional.empty();
        /* Default constructor for creating new Accounts */
    }

    @Override
    @NonNull
    public AccountBuilder key(@Nullable HederaKey key) {
        this.key = key;
        return this;
    }

    @Override
    @NonNull
    public AccountBuilder balance(long value) {
        if (value < 0 || value > (50_000_000_000L * HBARS_TO_TINYBARS)) {
            throw new IllegalArgumentException("Value cannot be < 0 or more than 50B hbar");
        }

        this.balance = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder expiry(long value) {
        this.expiry = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder memo(String value) {
        this.memo = Objects.requireNonNull(value);
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder deleted(boolean value) {
        this.deleted = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder receiverSigRequired(boolean value) {
        this.receiverSigRequired = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder numberOfOwnedNfts(long value) {
        this.numberOfOwnedNfts = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder maxAutoAssociations(int value) {
        this.maxAutoAssociations = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder usedAutoAssociations(int value) {
        this.usedAutoAssociations = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder numAssociations(int value) {
        this.numAssociations = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder numPositiveBalances(int value) {
        this.numPositiveBalances = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder ethereumNonce(long value) {
        this.ethereumNonce = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder stakedToMe(long value) {
        this.stakedToMe = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder stakePeriodStart(long value) {
        this.stakePeriodStart = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder stakedNum(long value) {
        this.stakedNum = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder declineReward(boolean value) {
        this.declineReward = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder stakeAtStartOfLastRewardedPeriod(long value) {
        this.stakeAtStartOfLastRewardedPeriod = value;
        return this;
    }

    @NonNull
    @Override
    public AccountBuilder autoRenewAccountNumber(long value) {
        this.autoRenewAccountNumber = value;
        return this;
    }

    @Override
    @NonNull
    public AccountBuilder accountNumber(long value) {
        this.accountNumber = value;
        return this;
    }

    @Override
    @NonNull
    public AccountBuilder alias(byte[] value) {
        this.alias = Optional.of(value);
        return this;
    }

    @Override
    @NonNull
    public AccountBuilder isSmartContract(boolean value) {
        this.isSmartContract = value;
        return this;
    }

    @Override
    @NonNull
    public AccountBuilder autoRenewSecs(long value) {
        this.autoRenewSecs = value;
        return this;
    }

    @Override
    @NonNull
    public Account build() {
        return new AccountImpl(
                accountNumber,
                alias, // 0 byte array if not set
                key, // null if the user hasn't set it
                expiry,
                balance,
                memo,
                deleted,
                isSmartContract,
                receiverSigRequired,
                numberOfOwnedNfts,
                maxAutoAssociations,
                usedAutoAssociations,
                numAssociations,
                numPositiveBalances,
                ethereumNonce,
                stakedToMe,
                stakePeriodStart,
                stakedNum,
                declineReward,
                stakeAtStartOfLastRewardedPeriod,
                autoRenewAccountNumber,
                autoRenewSecs);
    }
}
