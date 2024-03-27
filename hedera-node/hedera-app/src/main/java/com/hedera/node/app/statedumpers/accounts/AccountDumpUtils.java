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

package com.hedera.node.app.statedumpers.accounts;

import static com.hedera.node.app.service.mono.statedumpers.accounts.AccountDumpUtils.reportOnAccounts;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.statedumpers.DumpCheckpoint;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.node.app.service.mono.statedumpers.utils.Writer;
import com.hedera.node.app.state.merkle.disk.OnDiskKey;
import com.hedera.node.app.state.merkle.disk.OnDiskValue;
import com.swirlds.virtualmap.VirtualMap;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class AccountDumpUtils {
    private AccountDumpUtils() {
        // Utility class
    }

    public static void dumpModAccounts(
            @NonNull final Path path,
            @NonNull final VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts,
            @NonNull final DumpCheckpoint checkpoint) {
        try (@NonNull final var writer = new Writer(path)) {
            BBMHederaAccount[] dumpableAccounts = gatherAccounts(accounts);
            reportOnAccounts(writer, dumpableAccounts);
            System.out.printf(
                    "=== mod accounts report is %d bytes at checkpoint %s%n", writer.getSize(), checkpoint.name());
        }
    }

    @NonNull
    public static BBMHederaAccount[] gatherAccounts(
            @NonNull VirtualMap<OnDiskKey<AccountID>, OnDiskValue<Account>> accounts) {
        final var accountsToReturn = new ConcurrentLinkedQueue<BBMHederaAccount>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();

        try {
            VirtualMapLike.from(accounts)
                    .extractVirtualMapData(
                            getStaticThreadManager(),
                            p -> {
                                processed.incrementAndGet();
                                accountsToReturn.add(fromMod(p.right()));
                            },
                            threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of accounts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final var accountsArr = accountsToReturn.toArray(new BBMHederaAccount[0]);
        Arrays.parallelSort(
                accountsArr, Comparator.comparingLong(a -> a.accountId().accountNum()));
        System.out.printf("=== %d accounts iterated over (%d saved)%n", processed.get(), accountsArr.length);

        return accountsArr;
    }

    public static BBMHederaAccount fromMod(OnDiskValue<Account> account) {
        return new BBMHederaAccount(
                account.getValue().accountId(),
                account.getValue().alias(),
                account.getValue().key(),
                account.getValue().expirationSecond(),
                account.getValue().tinybarBalance(),
                account.getValue().memo(),
                account.getValue().deleted(),
                account.getValue().stakedToMe(),
                account.getValue().stakePeriodStart(),
                account.getValue().stakedId(),
                account.getValue().declineReward(),
                account.getValue().receiverSigRequired(),
                account.getValue().headTokenId(),
                account.getValue().headNftId(),
                account.getValue().headNftSerialNumber(),
                account.getValue().numberOwnedNfts(),
                account.getValue().maxAutoAssociations(),
                account.getValue().usedAutoAssociations(),
                account.getValue().numberAssociations(),
                account.getValue().smartContract(),
                account.getValue().numberPositiveBalances(),
                account.getValue().ethereumNonce(),
                account.getValue().stakeAtStartOfLastRewardedPeriod(),
                account.getValue().autoRenewAccountId(),
                account.getValue().autoRenewSeconds(),
                account.getValue().contractKvPairsNumber(),
                account.getValue().cryptoAllowances(),
                account.getValue().approveForAllNftAllowances(),
                account.getValue().tokenAllowances(),
                account.getValue().numberTreasuryTitles(),
                account.getValue().expiredAndPendingRemoval(),
                account.getValue().firstContractStorageKey(),
                account.isImmutable(),
                account.getValue().hasStakedNodeId() ? account.getValue().stakedNodeId() : -1);
    }
}
