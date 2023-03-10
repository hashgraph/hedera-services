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

package com.hedera.test.utils;

import static com.hedera.hapi.node.base.ResponseCodeEnum.ACCOUNT_IS_IMMUTABLE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MODIFYING_IMMUTABLE_CONTRACT;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.evm.store.models.HederaEvmAccount.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.numFromEvmAddress;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.accounts.Account;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import org.apache.commons.lang3.NotImplementedException;

public class TestFixturesKeyLookup implements AccountAccess {
    private final ReadableKVState<String, Long> aliases;
    private final ReadableKVState<EntityNumVirtualKey, HederaAccount> accounts;

    public TestFixturesKeyLookup(@NonNull final ReadableStates states) {
        this.accounts = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    @Override
    public KeyOrLookupFailureReason getKey(final AccountID idOrAlias) {
        final var account = accounts.get(accountNumOf(idOrAlias));
        if (account == null) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        }
        return Optional.of(account.getAccountKey())
                .map(key -> validateKey(key, false))
                .orElse(withFailureReason(INVALID_ACCOUNT_ID));
    }

    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias) {
        final var account = accounts.get(accountNumOf(idOrAlias));
        if (account == null) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        } else {
            return Optional.of(account.getAccountKey())
                    .map(key -> validateKey(key, false))
                    .filter(reason -> reason.failed() || account.isReceiverSigRequired())
                    .orElse(PRESENT_BUT_NOT_REQUIRED);
        }
    }

    @Override
    public KeyOrLookupFailureReason getKey(ContractID idOrAlias) {
        final var account = accounts.get(accountNumOf(asAccount(idOrAlias)));
        if (account == null) {
            return withFailureReason(INVALID_CONTRACT_ID);
        } else if (account.isDeleted() || !account.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        }
        return validateKey(account.getAccountKey(), true);
    }

    private AccountID asAccount(final ContractID idOrAlias) {
        return new AccountID.Builder()
                .realmNum(idOrAlias.realmNum())
                .shardNum(idOrAlias.shardNum())
                .accountNum(idOrAlias.contractNum().orElse(0L))
                .build();
    }

    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(ContractID idOrAlias) {
        final var account = accounts.get(accountNumOf(asAccount(idOrAlias)));
        if (account == null || account.isDeleted() || !account.isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        } else {
            final var key = account.getAccountKey();
            final var keyResult = validateKey(key, true);
            if (account.isReceiverSigRequired()) {
                return keyResult;
            } else {
                return PRESENT_BUT_NOT_REQUIRED;
            }
        }
    }

    // how to deal this ?
    @NonNull
    @Override
    public Optional<Account> getAccountById(@NonNull AccountID accountOrAlias) {
        throw new NotImplementedException("getAccountById not implemented");
    }

    private KeyOrLookupFailureReason validateKey(final JKey key, final boolean isContractKey) {
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

    private EntityNumVirtualKey accountNumOf(final AccountID id) {
        if (isAlias(PbjConverter.fromPbj(id))) {
            final var alias = id.alias().orElse(null);
            if (alias != null) {
                if (alias.getLength() == EVM_ADDRESS_SIZE) {
                    final var evmAddress = PbjConverter.fromPbj(alias).toByteArray();
                    if (isMirror(evmAddress)) {
                        return EntityNumVirtualKey.fromLong(numFromEvmAddress(evmAddress));
                    }
                }
                final var value = aliases.get(alias.asUtf8String());
                return EntityNumVirtualKey.fromLong(value != null ? value : 0L);
            }
        }
        return EntityNumVirtualKey.fromLong(id.accountNum().orElse(0L));
    }
}
