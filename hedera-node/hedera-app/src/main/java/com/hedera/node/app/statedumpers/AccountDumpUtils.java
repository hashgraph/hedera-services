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

package com.hedera.node.app.statedumpers;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.swirlds.state.merkle.disk.OnDiskKey;
import com.swirlds.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountDumpUtils {

    private AccountDumpUtils() {
        // Utility class
    }

    public static void dumpModAccounts(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            @NonNull final DumpCheckpoint checkpoint, final JsonWriter jsonWriter) {
        final var accountArr = gatherAccounts(accounts);
        jsonWriter.write(accountArr, path.toString());
        System.out.printf("Accounts with size %d dumped at checkpoint %s%n", accountArr.length, checkpoint.name());
    }

    @NonNull
    public static Account[] gatherAccounts(
            @NonNull VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts) {
        final Queue<Account> accountsToReturn = new ConcurrentLinkedQueue<>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    accounts,
                    p -> {
                        processed.incrementAndGet();
                        accountsToReturn.add(p.right().getValue());
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of accounts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }
        System.out.printf("=== %d accounts iterated over (%d saved)%n", processed.get(), accountsToReturn.size());
        return accountsToReturn.toArray(Account[]::new);
    }
}
