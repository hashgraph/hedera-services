/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.schemas;

import static com.hedera.node.app.service.token.impl.TokenServiceImpl.ACCOUNTS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V050TokenSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V050TokenSchema.class);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(50).patch(0).build();

    public V050TokenSchema() {
        super(VERSION);
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        @SuppressWarnings("unchecked")
        final List<Map.Entry<ContractID, Bytes>> migratedFirstKeys =
                (List<Map.Entry<ContractID, Bytes>>) ctx.sharedValues().getOrDefault("MIGRATED_FIRST_KEYS", List.of());
        if (migratedFirstKeys.isEmpty()) {
            return;
        }
        final WritableKVState<AccountID, Account> writableAccounts =
                ctx.newStates().get(ACCOUNTS_KEY);
        migratedFirstKeys.forEach(entry -> {
            final var accountId = AccountID.newBuilder()
                    .accountNum(entry.getKey().contractNumOrThrow())
                    .build();
            final var account = writableAccounts.getForModify(accountId);
            if (account == null) {
                log.warn("Contract account {} not found in the new state", entry.getKey());
            } else {
                writableAccounts.put(
                        accountId,
                        account.copyBuilder()
                                .firstContractStorageKey(entry.getValue())
                                .build());
            }
        });
    }
}
