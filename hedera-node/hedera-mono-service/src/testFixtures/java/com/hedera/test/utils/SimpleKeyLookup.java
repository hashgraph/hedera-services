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
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.isAlias;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.numFromEvmAddress;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.PRESENT_BUT_NOT_REQUIRED;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withFailureReason;
import static com.hedera.node.app.spi.KeyOrLookupFailureReason.withKey;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.KeyOrLookupFailureReason;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;

public class SimpleKeyLookup implements AccountKeyLookup {
    private final State<ByteString, Long> aliases;
    private final State<Long, HederaAccount> accounts;

    public SimpleKeyLookup(@NonNull final States states) {
        this.accounts = states.get("ACCOUNTS");
        this.aliases = states.get("ALIASES");
    }

    @Override
    public KeyOrLookupFailureReason getKey(final AccountID idOrAlias) {
        return accounts.get(accountNumOf(idOrAlias))
                .map(HederaAccount::getAccountKey)
                .map(this::validateKey)
                .orElse(withFailureReason(INVALID_ACCOUNT_ID));
    }

    @Override
    public KeyOrLookupFailureReason getKeyIfReceiverSigRequired(final AccountID idOrAlias) {
        final var account = accounts.get(accountNumOf(idOrAlias));
        if (account.isEmpty()) {
            return withFailureReason(INVALID_ACCOUNT_ID);
        } else {
            return account.map(HederaAccount::getAccountKey)
                    .map(this::validateKey)
                    .filter(reason -> reason.failed() || account.get().isReceiverSigRequired())
                    .orElse(PRESENT_BUT_NOT_REQUIRED);
        }
    }

    private KeyOrLookupFailureReason validateKey(final JKey key) {
        if (key == null) {
            throw new IllegalArgumentException("Provided Key is null");
        }
        if (key.isEmpty()) {
            return withFailureReason(ALIAS_IS_IMMUTABLE);
        }
        return withKey(key);
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
