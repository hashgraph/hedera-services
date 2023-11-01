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

import static com.hedera.node.app.service.evm.utils.EthSigsUtils.recoverAddressFromPubKey;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAliasSizeGreaterThanEvmAddress;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isOfEvmAddressSize;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.evm.contracts.execution.StaticProperties;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Optional;

/**
 * Default implementation of {@link ReadableAccountStore}
 */
public class ReadableAccountStoreImpl implements ReadableAccountStore {
    private static final byte[] MIRROR_PREFIX = new byte[12];

    static {
        /* A placeholder to store the 12-byte prefix (4-byte shard and 8-byte realm) that marks an EVM
         * address as a "mirror" address that follows immediately from a <shard>.<realm>.<num> id. */
        System.arraycopy(Longs.toByteArray(StaticProperties.getShard()), 4, MIRROR_PREFIX, 0, 4);
        System.arraycopy(Longs.toByteArray(StaticProperties.getRealm()), 0, MIRROR_PREFIX, 4, 8);
    }

    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<AccountID, Account> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final ReadableKVState<ProtoBytes, AccountID> aliases;

    /**
     * Create a new {@link ReadableAccountStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableAccountStoreImpl(@NonNull final ReadableStates states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    protected <T extends ReadableKVState<AccountID, Account>> T accountState() {
        return (T) accountState;
    }

    protected <T extends ReadableKVState<ProtoBytes, AccountID>> T aliases() {
        return (T) aliases;
    }

    public static boolean isMirror(final Bytes bytes) {
        return bytes.matchesPrefix(MIRROR_PREFIX);
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}
     *
     * @param accountID the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     *     Optional} otherwise
     */
    @Override
    @Nullable
    public Account getAccountById(@NonNull final AccountID accountID) {
        return getAccountLeaf(accountID);
    }

    @Override
    @Nullable
    public AccountID getAccountIDByAlias(@NonNull final Bytes alias) {
        return aliases.get(new ProtoBytes(alias));
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
    protected Account getAccountLeaf(@NonNull final AccountID id) {
        // Get the account number based on the account identifier. It may be null.
        final var accountOneOf = id.account();
        final Long accountNum =
                switch (accountOneOf.kind()) {
                    case ACCOUNT_NUM -> accountOneOf.as();
                    case ALIAS -> {
                        final Bytes alias = accountOneOf.as();
                        if (isOfEvmAddressSize(alias) && isMirror(alias)) {
                            yield fromMirror(alias);
                        } else {
                            final var entityId = unaliasWithEvmAddressConversionIfNeeded(alias);
                            yield entityId == null ? null : entityId.accountNumOrThrow();
                        }
                    }
                    case UNSET -> 0L;
                };

        return accountNum == null
                ? null
                : accountState.get(AccountID.newBuilder()
                        .realmNum(id.realmNum())
                        .shardNum(id.shardNum())
                        .accountNum(accountNum)
                        .build());
    }

    /**
     * Returns the {@link AccountID} referenced by the given alias, whether it is a direct reference or
     * an "indirect" reference by a proto ECDSA key whose implied EVM address is the alias.
     *
     * @param alias the alias to look up
     * @return the account id referenced by the alias, or null if not found
     */
    private @Nullable AccountID unaliasWithEvmAddressConversionIfNeeded(@NonNull final Bytes alias) {
        final var maybeDirectReference = aliases.get(new ProtoBytes(alias));
        if (maybeDirectReference != null) {
            return maybeDirectReference;
        } else {
            try {
                final var protoKey = Key.PROTOBUF.parseStrict(alias.toReadableSequentialData());
                if (protoKey.hasEcdsaSecp256k1()) {
                    final var evmAddress = recoverAddressFromPubKey(protoKey.ecdsaSecp256k1OrThrow());
                    return evmAddress.length() > 0 ? aliases.get(new ProtoBytes(evmAddress)) : null;
                } else {
                    return null;
                }
            } catch (IOException ignore) {
                return null;
            }
        }
    }

    /**
     * Returns the contract leaf for the given contract id. If the contract doesn't exist returns
     * {@code Optional.empty()}
     *
     * @param id given contract number
     * @return merkle leaf for the given contract number
     */
    @Nullable
    private Account getContractLeaf(@NonNull final ContractID id) {
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
                        var entityNum = aliases.get(new ProtoBytes(evmAddress));

                        // If we didn't find an alias, we will want to auto-create this account. But
                        // we don't want to auto-create an account if there is already another
                        // account in the system with the same EVM address that we would have auto-created.
                        if (isAliasSizeGreaterThanEvmAddress(evmAddress) && entityNum == null) {
                            // if we don't find entity num for key alias we can try to derive EVM
                            // address from it and look it up
                            final var evmKeyAliasAddress = keyAliasToEVMAddress(evmAddress);
                            if (evmKeyAliasAddress != null) {
                                entityNum = aliases.get(new ProtoBytes(Bytes.wrap(evmKeyAliasAddress)));
                            }
                        }
                        yield entityNum == null ? 0L : entityNum.accountNum();
                    }
                    case UNSET -> 0L;
                };

        return contractNum == null
                ? null
                : accountState.get(
                        AccountID.newBuilder().accountNum(contractNum).build());
    }

    @Override
    public long getNumberOfAccounts() {
        return accountState.size();
    }

    private static long numFromEvmAddress(final Bytes bytes) {
        return bytes.getLong(12);
    }

    private static long numOfMirror(final Bytes evmAddress) {
        return evmAddress.getLong(12);
    }

    static Long fromMirror(final Bytes evmAddress) {
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
}
