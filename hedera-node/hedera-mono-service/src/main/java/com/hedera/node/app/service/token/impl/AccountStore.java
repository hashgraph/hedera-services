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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.service.token.util.AliasUtils.MISSING_NUM;
import static com.hedera.node.app.service.token.util.AliasUtils.fromMirror;
import static com.hedera.node.app.service.token.util.AliasUtils.isMirror;
import static com.hedera.services.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.util.Optional;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Provides methods for interacting with the underlying data storage mechanisms for working with
 * Accounts.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public final class AccountStore {
    /** The underlying data storage class that holds the account data. */
    private final State<Long, MerkleAccount> accountState;
    /** The underlying data storage class that holds the aliases data built from the state. */
    private final State<ByteString, Long> aliases;

    /**
     * Create a new {@link AccountStore} instance.
     *
     * @param states The state to use.
     */
    public AccountStore(@Nonnull States states) {
        this.accountState = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    // In the future there will be an Account model object to retrieve all fields from MerkleAccount.
    // For Sig requirements we just need Key from the accounts.
    public record KeyOrReason(@Nullable HederaKey key, @Nullable ResponseCodeEnum reason) {
        public boolean failed(){
            return reason != OK;
        }
    }

    public KeyOrReason getKey(final AccountID idOrAlias) {
        final var account = getAccountLeaf(idOrAlias);
        if(account.isEmpty()){
            return new KeyOrReason(null, INVALID_ACCOUNT_ID);
        }
        return new KeyOrReason(account.get().getAccountKey(), OK);
    }

    /**
     * Returns the account leaf for the given account number. If the account doesn't exist throws
     * {@link IllegalArgumentException}
     *
     * @param id given account number
     * @return merkle leaf for the given account number
     */
    private Optional<MerkleAccount> getAccountLeaf(final AccountID id) {
        final var accountNum = getAccountNum(id);
        if (accountNum == MISSING_NUM) {
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
            final var num = aliases.get(alias);
            if (num.isPresent()) {
                return num.get();
            }
            return MISSING_NUM;
        }
        return id.getAccountNum();
    }
}
