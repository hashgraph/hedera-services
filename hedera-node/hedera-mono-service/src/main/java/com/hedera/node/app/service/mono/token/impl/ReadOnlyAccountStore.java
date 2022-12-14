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
package com.hedera.node.app.service.mono.token.impl;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.token.util.AliasUtils.MISSING_NUM;
import static com.hedera.node.app.service.mono.token.util.AliasUtils.fromMirror;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms
 * for working with Accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public final class ReadOnlyAccountStore implements AccountKeyLookup {
    /** The underlying data storage class that holds the account data. */
    private final State<Long, MerkleAccount> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final State<ByteString, Long> aliases;

    /**
     * Create a new {@link ReadOnlyAccountStore} instance.
     *
     * @param states The state to use.
     */
    public ReadOnlyAccountStore(@NonNull final States states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKey(final AccountID idOrAlias) {
        final var account = getAccountLeaf(idOrAlias);
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }
        return validateKey(account.get().getAccountKey());
    }

    /** {@inheritDoc} */
    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias) {
        final var account = getAccountLeaf(idOrAlias);
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }

        if (!account.get().isReceiverSigRequired()) {
            return PRESENT_BUT_NOT_REQUIRED;
        }
        return validateKey(account.get().getAccountKey());
    }

    /**
     * Returns the account leaf for the given account number. If the account doesn't exist returns
     * {@code Optional.empty()}
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    private Optional<MerkleAccount> getAccountLeaf(final AccountID id) {
        final var accountNum = getAccountNum(id);
        if (accountNum.equals(MISSING_NUM)) {
            return Optional.empty();
        }
        return accountState.get(accountNum);
    }

    /**
     * Get account number if the provided account id is an alias. If not, returns the account's
     * number
     *
     * @param id provided account id
     * @return account number
     */
    private Long getAccountNum(final AccountID id) {
        if (isAlias(id)) {
            final var alias = id.getAlias();
            if (alias.size() == EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    return fromMirror(evmAddress);
                }
            }
            return aliases.get(alias).orElse(MISSING_NUM);
        }
        return id.getAccountNum();
    }

    private KeyOrLookupFailureReason validateKey(final JKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Provided Key is null");
        }
        if (key.isEmpty()) {
            // FUTURE : need new response code ACCOUNT_IS_IMMUTABLE
            return withFailureReason(ALIAS_IS_IMMUTABLE);
        }
        return withKey(key);
    }
}
