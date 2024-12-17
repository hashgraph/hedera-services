/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.AccountID.AccountOneOfType.ACCOUNT_NUM;
import static com.hedera.node.app.service.token.AliasUtils.asKeyFromAliasOrElse;
import static com.hedera.node.app.service.token.AliasUtils.extractEvmAddress;
import static com.hedera.node.app.service.token.AliasUtils.extractIdFromAddressAlias;
import static com.hedera.node.app.service.token.AliasUtils.isEntityNumAlias;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Default implementation of {@link ReadableAccountStore}.
 */
public class ReadableAccountStoreImpl implements ReadableAccountStore {
    /** The underlying data storage class that holds the account data. */
    private final ReadableKVState<AccountID, Account> accountState;
    /**
     * The underlying data storage class that holds the aliases data built from the state. An alias can only be defined
     * at the time an account (or contract account) is created, and cannot be changed (except when the contract or
     * account is deleted, we could remove it then).
     *
     * <p>An alias may either be:
     * <ul>
     *     <li>A "long-zero" address (sometimes called a "mirror" address). This form takes the [shard].[realm].[num]
     *     and converts it into a single 20-byte long address. It is called "long-zero" because in the default network
     *     where shard and realm are both 0, it looks like a lot of zeros followed by a few bytes of info. Long zero
     *     aliases are not "real", they are not stored in this map, and they are not stored on accounts. They are
     *     computed directly from the account ID.</li>
     *     <li>An EVM address. This is always 20 bytes long. It could be used by accounts or contracts. It can be
     *     specified as part of hollow-account creation or as part of a normal crypto-create transaction.</li>
     *     <li>A protobuf-encoded key. The key can either be an ECDSA SECP256K1 key, or an ED25519 key (or in the future
     *     we may support other types of keys). If, and only if, it is an ECDSA SECP256K1 key, we can extract an EVM
     *     address from the key. Users can refer to their account using either the protobuf-encoded key alias, or
     *     the corresponding EVM address in this one case.</li>
     * </ul>
     *
     * <p>This alias map contains no mappings from long-zero to account ID. Since we can compute the account ID from a
     * long-zero address, we don't need to store it in this map. If we did store it in this map, we would have an entry
     * for every account, NFT, and other entity, which seems utterly wasteful.
     *
     * <p>This alias map will contain a mapping from EVM address to corresponding Account ID, whether the EVM address
     * was the alias on the account, or was derived from an ECDSA SECP256K1 protobuf encoded key alias on the account.
     *
     * <p>This alias map will also contain the raw protobuf-encoded key alias, regardless of what type of
     * protobuf-encoded key was used (ED25519 or ECDSA SECP256K1).
     */
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

    /** Get the account state. Convenience method for auto-casting to the right kind of state (readable vs. writable) */
    protected <T extends ReadableKVState<AccountID, Account>> T accountState() {
        return (T) accountState;
    }

    /** Get the alias state. Convenience method for auto-casting to the right kind of state (readable vs. writable) */
    protected <T extends ReadableKVState<ProtoBytes, AccountID>> T aliases() {
        return (T) aliases;
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}. If the account has an alias
     * set on it, it doesn't look in the alias map to find the account ID and returns null
     *
     * @param accountID the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     * Optional} otherwise
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

    @Override
    public boolean containsAlias(@NonNull Bytes alias) {
        return aliases.contains(new ProtoBytes(alias));
    }

    @Override
    public boolean contains(@NonNull final AccountID accountID) {
        return accountState().contains(accountID);
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
        // The Account ID may be aliased, in which case we need to convert it to a number-based account ID first.
        requireNonNull(id);
        final var accountOneOf = id.account();
        return accountOneOf.kind() == ACCOUNT_NUM ? accountState.get(id) : null;
    }

    /**
     * Returns the {@link Account} for a given {@link AccountID}. If the account has an alias
     * set on it, looks in the alias map to find the account ID. This method should only be used in
     * {@code CryptoTransfer} since aliases are allowed only for auto-created accounts.
     *
     * @param accountID the {@code AccountID} which {@code Account is requested}
     * @return an {@link Optional} with the {@code Account}, if it was found, an empty {@code
     * Optional} otherwise
     */
    @Override
    @Nullable
    public Account getAliasedAccountById(@NonNull final AccountID accountID) {
        return getAliasedAccountLeaf(accountID);
    }

    /**
     * Returns the account leaf for the given account id. If the account doesn't exist, returns
     * {@link Optional}.
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    @Nullable
    protected Account getAliasedAccountLeaf(@NonNull final AccountID id) {
        // The Account ID may be aliased, in which case we need to convert it to a number-based account ID first.
        requireNonNull(id);
        final var accountId = lookupAliasedAccountId(id);
        return accountId == null ? null : accountState.get(accountId);
    }

    @Override
    public long getNumberOfAccounts() {
        return accountState.size();
    }

    /**
     * Given some {@link AccountID}, if it is an alias, then convert it to a number-based account ID. If it is not an
     * alias, then just return it. If the given id is bogus, containing neither an account number nor an alias, or
     * containing an alias that we simply don't know about, then return null.
     *
     * @param id The account ID that possibly has an alias to convert to an Account ID without an alias.
     * @return The result, or null if the id is invalid or there is no known alias-to-account mapping for it
     */
    @Nullable
    protected AccountID lookupAliasedAccountId(@NonNull final AccountID id) {
        final var accountOneOf = id.account();
        return switch (accountOneOf.kind()) {
            case ACCOUNT_NUM -> id;
            case ALIAS -> {
                // An alias may either be long-zero (in which case it isn't in our alias map), or it may be
                // any other form of valid alias (in which case it will be in the map). So we do a quick check
                // first to see if it is a valid long zero, and if not, then we look it up in the map.
                final Bytes alias = accountOneOf.as();
                if (isEntityNumAlias(alias)) {
                    yield id.copyBuilder()
                            .accountNum(extractIdFromAddressAlias(alias))
                            .build();
                }

                // Since it wasn't long-zero, we will just look up in the aliases map. It may be an EVM address alias,
                // in which case it is in the map, or it may be a protobuf-encoded key alias, in which case it *may*
                // also be in the map. When someone gives us a protobuf-encoded ECDSA key, we store both the alias to
                // the ECDSA key *and* the EVM address in the alias map. But if somebody only gives us the EVM address,
                // we cannot compute the ECDSA key from it, so we only store the EVM address in the alias map. So if we
                // do this look up and cannot find the answer, then we have to check if the key is an ECDSA key, and
                // if it is, we have to compute the EVM address from it, and then look up the EVM address in the map.
                final var found = aliases.get(new ProtoBytes(alias));
                if (found != null) yield found;
                yield aliases.get(new ProtoBytes(extractEvmAddress(asKeyFromAliasOrElse(alias, null))));
            }
            case UNSET -> null;
        };
    }

    public long sizeOfAccountState() {
        return accountState().size();
    }

    @Override
    public void warm(@NonNull final AccountID accountID) {
        final var unaliasedId = lookupAliasedAccountId(accountID);
        if (unaliasedId != null) {
            accountState.warm(unaliasedId);
        }
    }
}
