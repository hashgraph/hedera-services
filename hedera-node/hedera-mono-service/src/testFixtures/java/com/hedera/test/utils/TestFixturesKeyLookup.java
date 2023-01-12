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

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.*;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JContractIDKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import edu.umd.cs.findbugs.annotations.NonNull;

public class TestFixturesKeyLookup implements AccountKeyLookup {
    private final State<ByteString, Long> aliases;
    private final State<Long, HederaAccount> accounts;

    public TestFixturesKeyLookup(@NonNull final States states) {
        this.accounts = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    @Override
    public KeyOrLookupFailureReason getKey(final AccountID idOrAlias) {
        return accounts.get(accountNumOf(idOrAlias))
                .map(HederaAccount::getAccountKey)
                .map(this::validateAccountKey)
                .orElse(withFailureReason(INVALID_ACCOUNT_ID));
    }

    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias) {
        final var account = accounts.get(accountNumOf(idOrAlias));
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        } else {
            return account.map(HederaAccount::getAccountKey)
                    .map(this::validateAccountKey)
                    .filter(reason -> reason.failed() || account.get().isReceiverSigRequired())
                    .orElse(PRESENT_BUT_NOT_REQUIRED);
        }
    }

    @Override
    public KeyOrLookupFailureReason getKey(ContractID idOrAlias) {
        return accounts.get(accountNumOf(asAccount(idOrAlias)))
                .map(HederaAccount::getAccountKey)
                .map(this::validateContractKey)
                .orElse(withFailureReason(INVALID_CONTRACT_ID));
    }

    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(ContractID idOrAlias) {
        final var account = accounts.get(accountNumOf(asAccount(idOrAlias)));
        if (account.isEmpty()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        } else if (account.get().isDeleted() || !account.get().isSmartContract()) {
            return withFailureReason(INVALID_CONTRACT_ID);
        } else {
            return account.map(HederaAccount::getAccountKey)
                    .map(this::validateContractKey)
                    .filter(reason -> reason.failed() || account.get().isReceiverSigRequired())
                    .orElse(PRESENT_BUT_NOT_REQUIRED);
        }
    }

    private KeyOrLookupFailureReason validateContractKey(final JKey key) {
        return validateKey(key, true);
    }

    private KeyOrLookupFailureReason validateAccountKey(final JKey key) {
        return validateKey(key, false);
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

    private Long accountNumOf(final AccountID id) {
        if (isAlias(id)) {
            final var alias = id.getAlias();
            if (alias.size() == EVM_ADDRESS_SIZE) {
                final var evmAddress = alias.toByteArray();
                if (isMirror(evmAddress)) {
                    return numFromEvmAddress(evmAddress);
                }
            }
            return aliases.get(alias).orElse(0L);
        }
        return id.getAccountNum();
    }
}
