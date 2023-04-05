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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.state.migration.StateChildIndices.CONTRACT_STORAGE;

import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.swirlds.common.threading.manager.AdHocThreadManager;
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
            final VirtualMapDataAccess dataAccess) {
        final var contracts = initializingState.accounts();
        final VirtualMap<ContractKey, IterableContractValue> storage = initializingState.getChild(CONTRACT_STORAGE);
        final var linkRepairs = repairsFactory.from(contracts.getInMemoryAccounts(), storage);
        try {
            scanForReason("to build in-memory storage mapping and links", linkRepairs, storage, dataAccess);
            linkRepairs.fixAnyBrokenLinks();
        } catch (InterruptedException e) {
            log.error("Interrupted while re-building storage mapping and links", e);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void scanForReason(
            final String desc,
            final LinkRepairs linkRepairs,
            final VirtualMap<ContractKey, IterableContractValue> storage,
            final VirtualMapDataAccess dataAccess)
            throws InterruptedException {
        log.info("Scanning storage {} with {} threads", desc, THREAD_COUNT);
        final var watch = StopWatch.createStarted();
        dataAccess.extractVirtualMapData(
                AdHocThreadManager.getStaticThreadManager(), storage, linkRepairs, THREAD_COUNT);
        logDone(watch);
    }

    public interface LinkRepairsFactory {
        LinkRepairs from(
                MerkleMapLike<EntityNum, MerkleAccount> contracts,
                VirtualMap<ContractKey, IterableContractValue> storage);
    }

    private static void logDone(final StopWatch watch) {
        log.info("Done in {}ms", watch.getTime(TimeUnit.MILLISECONDS));
    }
}
