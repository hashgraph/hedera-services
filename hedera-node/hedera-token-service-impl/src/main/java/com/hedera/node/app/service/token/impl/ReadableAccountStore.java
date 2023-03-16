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

package com.hedera.node.app.service.token.impl;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static java.util.Objects.requireNonNull;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.node.app.service.evm.contracts.execution.StaticProperties;
import com.hedera.node.app.service.mono.Utils;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.entity.AccountBuilderImpl;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableAccountStore implements AccountAccess {
    public static final int EVM_ADDRESS_LEN = 20;
    private static final byte[] MIRROR_PREFIX = new byte[12];

    static {
        /* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
         * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
        System.arraycopy(Longs.toByteArray(StaticProperties.getShard()), 4, MIRROR_PREFIX, 0, 4);
        System.arraycopy(Longs.toByteArray(StaticProperties.getRealm()), 0, MIRROR_PREFIX, 4, 8);
    }

    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<EntityNumVirtualKey, MerkleAccount> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final ReadableKVState<String, EntityNumValue> aliases;

    /**
     * Create a new {@link ReadableAccountStore} instance.
     *
     * @param states The state to use.
     */
    public ReadableAccountStore(@NonNull final ReadableStates states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public KeyOrLookupFailureReason getKey(@NonNull final AccountID id) {
        requireNonNull(id);
        final var account = getAccountLeaf(id);
        return account == null ? withFailureReason(INVALID_ACCOUNT_ID) : validateKey(account.getAccountKey(), false);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(@NonNull final AccountID id) {
        requireNonNull(id);
        final var account = getAccountLeaf(id);
        if (account == null) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }

        final var responseIgnoringSigReq = validateKey(account.getAccountKey(), false);
        return (responseIgnoringSigReq.failed() || account.isReceiverSigRequired())
                ? responseIgnoringSigReq
                : PRESENT_BUT_NOT_REQUIRED;
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public KeyOrLookupFailureReason getKey(@NonNull final ContractID id) {
        requireNonNull(id);
        final var contract = getContractLeaf(id);
        if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }

        return validateKey(contract.getAccountKey(), true);
    }

    /** {@inheritDoc} */
    @NonNull
    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(@NonNull final ContractID id) {
        requireNonNull(id);
        final var contract = getContractLeaf(id);
        if (contract == null || contract.isDeleted() || !contract.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }

        final var responseIgnoringSigReq = validateKey(contract.getAccountKey(), true);
        if (responseIgnoringSigReq.failed() || contract.isReceiverSigRequired()) {
            return responseIgnoringSigReq;
        }
        return PRESENT_BUT_NOT_REQUIRED;
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}
     *
     * @param id the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     *     Optional} otherwise
     */
    @Override
    @NonNull
    public Optional<Account> getAccountById(@NonNull final AccountID id) {
        requireNonNull(id);
        // TODO Make sure we have tests for getAccount for all valid account IDs.
        final var account = getAccountLeaf(id);
        return Optional.ofNullable(account).map(accountLeaf -> mapAccount(id, accountLeaf));
    }

    /* Helper methods */

    /**
     * Returns the account leaf for the given account id. If the account doesn't exist, returns
     * {@link Optional}.
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    @Nullable
    private HederaAccount getAccountLeaf(@NonNull final AccountID id) {
        // Get the account number based on the account identifier. It may be null.
        final var accountOneOf = id.account();
        final Long accountNum =
                switch (accountOneOf.kind()) {
                    case ACCOUNT_NUM -> accountOneOf.as();
                    case ALIAS -> {
                        final Bytes alias = accountOneOf.as();
                        if (alias.length() == EVM_ADDRESS_LEN && isMirror(alias)) {
                            yield fromMirror(alias);
                        } else {
                            final var entityNum = aliases.get(alias.asUtf8String());
                            yield entityNum == null ? EntityNumValue.DEFAULT.num() : entityNum.num();
                        }
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return accountNum == null ? null : accountState.get(EntityNumVirtualKey.fromLong(accountNum));
    }

    /**
     * Returns the contract leaf for the given contract id. If the contract doesn't exist returns
     * {@code Optional.empty()}
     *
     * @param id given contract number
     * @return merkle leaf for the given contract number
     */
    @Nullable
    private HederaAccount getContractLeaf(@NonNull final ContractID id) {
        // Get the contract number based on the contract identifier. It may be null.
        final var contractOneOf = id.contract();
        final Long contractNum =
                switch (contractOneOf.kind()) {
                    case CONTRACT_NUM -> contractOneOf.as();
                    case EVM_ADDRESS -> {
                        // If the evm address is of "long-zero" format, then parse out the contract
                        // num from those bytes
                        final Bytes evmAddress = contractOneOf.as();
                        if (isMirror(evmAddress)) {
                            yield numOfMirror(evmAddress);
                        }

                        // The evm address is some kind of alias.
                        var entityNum = aliases.get(evmAddress.asUtf8String());

                        // If we didn't find an alias, we will want to auto-create this account. But
                        // we don't want to auto-create an account if there is already another
                        // account in the system with the same EVM address that we would have auto-created.
                        if (evmAddress.length() > EVM_ADDRESS_LEN && entityNum == null) {
                            // if we don't find entity num for key alias we can try to derive EVM
                            // address from it and look it up
                            final var evmKeyAliasAddress = keyAliasToEVMAddress(evmAddress);
                            if (evmKeyAliasAddress != null) {
                                entityNum = aliases.get(
                                        ByteString.copyFrom(evmKeyAliasAddress).toStringUtf8());
                            }
                        }
                        yield entityNum == null ? EntityNumValue.DEFAULT.num() : entityNum.num();
                    }
                    case UNSET -> EntityNumValue.DEFAULT.num();
                };

        return contractNum == null ? null : accountState.get(EntityNumVirtualKey.fromLong(contractNum));
    }

    private KeyOrLookupFailureReason validateKey(@Nullable final JKey key, final boolean isContractKey) {
        if (key == null || key.isEmpty()) {
            if (isContractKey) {
                return withFailureReason(MODIFYING_IMMUTABLE_CONTRACT);
            }
            return withFailureReason(ACCOUNT_IS_IMMUTABLE);
        } else if (isContractKey && key instanceof JContractIDKey) {
            return withFailureReason(MODIFYING_IMMUTABLE_CONTRACT);
        } else {
            return withKey(key);
        }
    }

    private static boolean isMirror(final Bytes bytes) {
        return bytes.matchesPrefix(MIRROR_PREFIX);
    }

    private static long numFromEvmAddress(final Bytes bytes) {
        return bytes.getLong(12);
    }

    private static long numOfMirror(final Bytes evmAddress) {
        return evmAddress.getLong(12);
    }

    private static Long fromMirror(final Bytes evmAddress) {
        return numFromEvmAddress(evmAddress);
    }

    @Nullable
    private static byte[] keyAliasToEVMAddress(final Bytes alias) {
        // NOTE: This implementation should be fixed when we (finally!) remove
        // JKey. The old JKey class needs a Google protobuf Key, so for now we
        // delegate to AliasManager. But this should be changed, so we don't
        // need AliasManager anymore.
        final var buf = new byte[Math.toIntExact(alias.length())];
        alias.getBytes(0, buf);
        return AliasManager.keyAliasToEVMAddress(ByteString.copyFrom(buf));
    }

    // Converts a HederaAccount into an Account
    private Account mapAccount(final AccountID idOrAlias, final HederaAccount account) {
        final var accountNum = idOrAlias.accountNumOrElse(0L);
        final var builder = new AccountBuilderImpl()
                .key(account.getAccountKey())
                .expiry(account.getExpiry())
                .balance(account.getBalance())
                .memo(account.getMemo())
                .deleted(account.isDeleted())
                .receiverSigRequired(account.isReceiverSigRequired())
                .numberOfOwnedNfts(account.getNftsOwned())
                .maxAutoAssociations(account.getMaxAutomaticAssociations())
                .usedAutoAssociations(account.getUsedAutoAssociations())
                .numAssociations(account.getNumAssociations())
                .numPositiveBalances(account.getNumPositiveBalances())
                .ethereumNonce(account.getEthereumNonce())
                .stakedToMe(account.getStakedToMe())
                .stakePeriodStart(account.getStakePeriodStart())
                .stakedNum(account.totalStake())
                .declineReward(account.isDeclinedReward())
                .stakeAtStartOfLastRewardedPeriod(account.getStakePeriodStart())
                .autoRenewSecs(account.getAutoRenewSecs())
                .accountNumber(accountNum)
                .isSmartContract(account.isSmartContract());
        if (account.getAutoRenewAccount() != null) {
            builder.autoRenewAccountNumber(account.getAutoRenewAccount().num());
        }
        if (account.getAlias() != null) {
            builder.alias(account.getAlias().toByteArray());
        }
        return builder.build();
    }

    @NonNull
    public Optional<HederaKey> asHederaKey(@NonNull final Key key) {
        requireNonNull(key);
        return Utils.asHederaKey(key);
    }
}
