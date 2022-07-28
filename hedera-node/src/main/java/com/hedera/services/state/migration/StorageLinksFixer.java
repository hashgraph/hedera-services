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
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.virtual.ContractKey;
import com.hedera.services.state.virtual.IterableContractValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.virtualmap.VirtualMap;
import java.util.concurrent.TimeUnit;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StorageLinksFixer {
    private static final Logger log = LogManager.getLogger(StorageLinksFixer.class);

    private StorageLinksFixer() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final int THREAD_COUNT = 32;

    public static void fixAnyBrokenLinks(
            final ServicesState initializingState,
            final LinkRepairsFactory repairsFactory,
            final VirtualMapDataAccess<ContractKey, IterableContractValue> dataAccess) {
        final var contracts = initializingState.accounts();
        final VirtualMap<ContractKey, IterableContractValue> storage =
                initializingState.getChild(CONTRACT_STORAGE);
        final var linkRepairs = repairsFactory.from(contracts, storage);
        try {
            scanForReason("to detect broken links", linkRepairs, storage, dataAccess);
            linkRepairs.markScanComplete();
            if (linkRepairs.hasBrokenLinks()) {
                scanForReason("to repair broken links", linkRepairs, storage, dataAccess);
                linkRepairs.fixAnyBrokenLinks();
            }
        } catch (InterruptedException e) {
            log.error("Interrupted while scanning for broken links", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void scanForReason(
            final String desc,
            final LinkRepairs linkRepairs,
            final VirtualMap<ContractKey, IterableContractValue> storage,
            final VirtualMapDataAccess<ContractKey, IterableContractValue> dataAccess)
            throws InterruptedException {
        log.info("Scanning storage {} with {} threads", desc, THREAD_COUNT);
        final var watch = StopWatch.createStarted();
        dataAccess.extractVirtualMapData(storage, linkRepairs, THREAD_COUNT);
        logDone(watch);
    }

    public interface LinkRepairsFactory {
        LinkRepairs from(
                MerkleMap<EntityNum, MerkleAccount> contracts,
                VirtualMap<ContractKey, IterableContractValue> storage);
    }

    private static void logDone(final StopWatch watch) {
        log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
    }
}
