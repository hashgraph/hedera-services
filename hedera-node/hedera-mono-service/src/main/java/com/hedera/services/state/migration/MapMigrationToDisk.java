package com.hedera.services.state.migration;

import com.hedera.services.ServicesState;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerklePayerRecords;
import com.hedera.services.state.virtual.EntityNumVirtualKey;
import com.hedera.services.state.virtual.VirtualMapFactory;
import com.hedera.services.state.virtual.entities.OnDiskAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.NonAtomicReference;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static com.hedera.services.state.migration.StateChildIndices.ACCOUNTS;
import static com.hedera.services.state.migration.StateChildIndices.PAYER_RECORDS;
import static com.hedera.services.utils.MiscUtils.forEach;

public class MapMigrationToDisk {
    private static final Logger log = LogManager.getLogger(MapMigrationToDisk.class);

    public static void migrateToDiskAsApropos(
            final int insertionsPerCopy,
            final ServicesState mutableState,
            final VirtualMapFactory virtualMapFactory,
            final Function<MerkleAccountState, OnDiskAccount> accountMigrator
    ) {
        final var insertionsSoFar = new AtomicInteger(0);
        final NonAtomicReference<VirtualMap<EntityNumVirtualKey, OnDiskAccount>> onDiskAccounts =
                new NonAtomicReference<>(virtualMapFactory.newOnDiskAccountStorage());

        final var inMemoryAccounts = mutableState.accounts();
        final MerkleMap<EntityNum, MerklePayerRecords> payerRecords = new MerkleMap<>();
        forEach(inMemoryAccounts, (num, account) -> {
            final var accountRecords = new MerklePayerRecords();
            account.records().forEach(accountRecords::offer);
            payerRecords.put(num, accountRecords);

            final var onDiskAccount = accountMigrator.apply(account.state());
            onDiskAccounts.get().put(new EntityNumVirtualKey(num.longValue()), onDiskAccount);
            if (insertionsSoFar.incrementAndGet() % insertionsPerCopy == 0) {
                final var onDiskAccountsCopy = onDiskAccounts.get().copy();
                onDiskAccounts.set(onDiskAccountsCopy);
            }
        });
        mutableState.setChild(ACCOUNTS, onDiskAccounts.get());
        mutableState.setChild(PAYER_RECORDS, payerRecords);
    }

    private MapMigrationToDisk() {
        throw new UnsupportedOperationException("Utility Class");
    }
}
