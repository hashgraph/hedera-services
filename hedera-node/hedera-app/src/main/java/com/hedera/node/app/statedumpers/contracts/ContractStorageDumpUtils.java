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

package com.hedera.node.app.statedumpers.contracts;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.statedumpers.utils.Writer;
import com.hedera.pbj.runtime.ParseException;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapMigration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Utility class for dumping contract storage from a virtual map.
 */
public class ContractStorageDumpUtils {
    private ContractStorageDumpUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Dumps the storage slots from the given virtual math to a file at the given path.
     * @param path the path to the file to write to
     * @param storage the virtual map to dump
     */
    public static void dumpStorage(@NonNull final Path path, @NonNull final VirtualMap storage) {
        final var slots = gatherSlots(storage);
        try (@NonNull final var writer = new Writer(path)) {
            slots.forEach(slot -> writer.writeln("0.0.%d[%s] -> (%s,%s,%s)"
                    .formatted(
                            slot.contractNum(),
                            slot.key().key().toHex(),
                            slot.value().previousKey().toHex(),
                            slot.value().value().toHex(),
                            slot.value().nextKey().toHex())));
        }
    }

    public record Slot(@NonNull SlotKey key, @NonNull SlotValue value) implements Comparable<Slot> {
        private static final Comparator<Slot> COMPARATOR = Comparator.comparingLong(Slot::contractNum)
                .thenComparing(s -> s.key().key());

        @Override
        public int compareTo(@NonNull final Slot that) {
            return COMPARATOR.compare(this, that);
        }

        public long contractNum() {
            return key().contractIDOrThrow().contractNumOrThrow();
        }
    }

    @NonNull
    private static List<Slot> gatherSlots(@NonNull final VirtualMap storage) {
        final var slotsToReturn = new ConcurrentLinkedQueue<Slot>();
        final var threadCount = 8;
        final var processed = new AtomicInteger();
        try {
            VirtualMapMigration.extractVirtualMapData(
                    getStaticThreadManager(),
                    storage,
                    p -> {
                        try {
                            processed.incrementAndGet();
                            final SlotKey slotKey = SlotKey.PROTOBUF.parse(p.left());
                            final SlotValue slotValue = SlotValue.PROTOBUF.parse(requireNonNull(p.right()));
                            slotsToReturn.add(new Slot(slotKey, slotValue));
                        } catch (final ParseException e) {
                            throw new RuntimeException("Failed to parse a slot key/value", e);
                        }
                    },
                    threadCount);
        } catch (final InterruptedException ex) {
            System.err.println("*** Traversal of contracts virtual map interrupted!");
            Thread.currentThread().interrupt();
        }

        final List<Slot> answer = new ArrayList(slotsToReturn);
        answer.sort(Comparator.naturalOrder());
        System.out.printf("=== %d slots iterated over%n", answer.size());
        return answer;
    }
}
