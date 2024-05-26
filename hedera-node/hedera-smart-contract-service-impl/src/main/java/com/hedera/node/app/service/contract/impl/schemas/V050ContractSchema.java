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

package com.hedera.node.app.service.contract.impl.schemas;

import static com.hedera.hapi.util.HapiUtils.CONTRACT_ID_COMPARATOR;
import static com.hedera.node.app.service.contract.impl.schemas.V0490ContractSchema.STORAGE_KEY;
import static java.util.Comparator.naturalOrder;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.spi.state.MigrationContext;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableKVState;
import com.swirlds.state.spi.WritableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class V050ContractSchema extends Schema {
    private static final Logger log = LogManager.getLogger(V050ContractSchema.class);

    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(50).patch(0).build();

    private static final AtomicReference<Map<ContractID, List<StorageMapping>>> MIGRATED_STORAGE_LINKS =
            new AtomicReference<>();

    public V050ContractSchema() {
        super(VERSION);
    }

    public record StorageMapping(@NonNull SlotKey key, @NonNull SlotValue value) implements Comparable<StorageMapping> {
        @Override
        public int compareTo(@NonNull final StorageMapping that) {
            return this.key().key().compareTo(that.key().key());
        }
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        requireNonNull(ctx);
        final ReadableKVState<SlotKey, SlotValue> storage = ctx.previousStates().get(STORAGE_KEY);
        final Map<ContractID, List<StorageMapping>> mappings = new HashMap<>();
        // Collect all storage mappings from the previous state
        storage.keys()
                .forEachRemaining(key -> mappings.computeIfAbsent(key.contractIDOrThrow(), ignore -> new ArrayList<>())
                        .add(new StorageMapping(key, requireNonNull(storage.get(key)))));

        // Relink any broken storage mappings, while removing any that were already intact
        new ArrayList<>(mappings.keySet()).forEach(contractId -> mappings.compute(contractId, this::fixedIfBroken));

        final List<ContractID> contractIdsToMigrate = new ArrayList<>(mappings.keySet());
        log.info("Previous state had {} contracts with broken storage links", contractIdsToMigrate.size());
        if (!contractIdsToMigrate.isEmpty()) {
            contractIdsToMigrate.sort(CONTRACT_ID_COMPARATOR);
            final WritableKVState<SlotKey, SlotValue> writableStorage =
                    ctx.newStates().get(STORAGE_KEY);
            // And finally update the new state with the fixed mappings
            contractIdsToMigrate.forEach(contractId ->
                    mappings.get(contractId).forEach(mapping -> writableStorage.put(mapping.key(), mapping.value())));
            log.info("Migrated mappings are {}", mappings);
            ctx.sharedValues()
                    .put(
                            "MIGRATED_FIRST_KEYS",
                            contractIdsToMigrate.stream()
                                    .map(contractId -> new AbstractMap.SimpleImmutableEntry<>(
                                            contractId,
                                            mappings.get(contractId)
                                                    .getFirst()
                                                    .key()
                                                    .key()))
                                    .toList());
        }
    }

    private @Nullable List<StorageMapping> fixedIfBroken(
            @NonNull final ContractID contractId, @NonNull final List<StorageMapping> mappings) {
        requireNonNull(contractId);
        requireNonNull(mappings);
        return hasIntactLinks(mappings) ? null : fixBrokenLinks(mappings);
    }

    private @NonNull List<StorageMapping> fixBrokenLinks(@NonNull final List<StorageMapping> mappings) {
        mappings.sort(naturalOrder());
        final List<StorageMapping> relinked = new ArrayList<>(mappings.size());
        for (int i = 0, n = mappings.size(); i < n; i++) {
            final var previousKey =
                    i == 0 ? Bytes.EMPTY : mappings.get(i - 1).key().key();
            final var nextKey =
                    i == n - 1 ? Bytes.EMPTY : mappings.get(i + 1).key().key();
            final var mapping = mappings.get(i);
            relinked.add(new StorageMapping(
                    mapping.key(), new SlotValue(mapping.value().value(), previousKey, nextKey)));
        }
        return relinked;
    }

    private boolean hasIntactLinks(@NonNull final List<StorageMapping> mappings) {
        StorageMapping first = null;
        StorageMapping last = null;
        final Map<Bytes, Integer> locations = HashMap.newHashMap(mappings.size());
        int location = 0;
        for (final var mapping : mappings) {
            if (Bytes.EMPTY.equals(mapping.value().previousKey())) {
                first = mapping;
            }
            if (Bytes.EMPTY.equals(mapping.value().nextKey())) {
                last = mapping;
            }
            locations.put(mapping.key().key(), location++);
        }
        if (first != null && last != null) {
            // Given a first and last mapping, we can check if the chain is intact; that is, if...
            //   (1) Each mapping points to a next mapping whose previous key is that mapping's key; and,
            //   (2) Following the chain from the first mapping leads to the last mapping; and,
            //   (3) Every mapping in the chain is seen exactly once.
            int seen = 1;
            StorageMapping current = first;
            for (int i = 0, n = mappings.size() - 1; i < n; i++, seen++) {
                final var next = locations.get(current.value().nextKey());
                if (next == null
                        || !current.key()
                                .key()
                                .equals(mappings.get(next).value().previousKey())) {
                    break;
                } else {
                    current = mappings.get(next);
                }
            }
            return seen == mappings.size() && current == last;
        }
        return false;
    }
}
