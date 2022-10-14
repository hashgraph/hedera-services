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
package com.hedera.services.state.migration;

import static com.hedera.services.state.migration.StateChildIndices.CONTRACT_STORAGE;

import com.hedera.services.ServicesState;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.ContractValue;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.state.virtual.IterableStorageUtils;
import com.hedera.services.store.contracts.SizeLimitedStorage;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ReleaseTwentySixMigration {
    private static final Logger log = LogManager.getLogger(ReleaseTwentySixMigration.class);

    public static final int THREAD_COUNT = 32;
    public static final int INSERTIONS_PER_COPY = 10_000;

    public static void makeStorageIterable(
            final ServicesState initializingState,
            final MigratorFactory migratorFactory,
            final VirtualMapDataAccess virtualMapDataAccess,
            final VirtualMap<ContractKey, IterableContractValue> iterableContractStorage) {
        final var contracts = initializingState.accounts();
        final VirtualMap<ContractKey, ContractValue> contractStorage =
                initializingState.getChild(CONTRACT_STORAGE);
        final var migrator =
                migratorFactory.from(
                        INSERTIONS_PER_COPY,
                        contracts,
                        IterableStorageUtils::overwritingUpsertMapping,
                        iterableContractStorage);
        try {
            log.info(
                    "Migrating contract storage into iterable VirtualMap with {} threads",
                    THREAD_COUNT);
            final var watch = StopWatch.createStarted();
            virtualMapDataAccess.extractVirtualMapData(contractStorage, migrator, THREAD_COUNT);
            logDone(watch);
        } catch (InterruptedException e) {
            log.error("Interrupted while making contract storage iterable", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        migrator.finish();
        initializingState.setChild(CONTRACT_STORAGE, migrator.getMigratedStorage());
    }

    private static void logDone(final StopWatch watch) {
        log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
    }

    @FunctionalInterface
    public interface MigratorFactory {
        KvPairIterationMigrator from(
                int insertionsPerCopy,
                AccountStorageAdapter contracts,
                SizeLimitedStorage.IterableStorageUpserter storageUpserter,
                VirtualMap<ContractKey, IterableContractValue> iterableContractStorage);
    }

    private ReleaseTwentySixMigration() {
        throw new UnsupportedOperationException("Utility class");
    }
}
