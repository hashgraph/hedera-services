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

package com.hedera.services.bdd.spec.utilops.streams.assertions;

import static com.hedera.services.bdd.spec.utilops.streams.assertions.BaseIdScreenedAssertion.baseFieldsMatch;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VisibleItemsAssertion implements RecordStreamAssertion {
    static final Logger log = LogManager.getLogger(VisibleItemsAssertion.class);

    private final HapiSpec spec;
    private final Set<String> unseenIds;
    private final CountDownLatch latch;
    private final Map<String, List<RecordStreamEntry>> entries = new HashMap<>();

    @Nullable
    private String lastSeenId = null;

    public VisibleItemsAssertion(@NonNull final HapiSpec spec, @NonNull final String... specTxnIds) {
        this.spec = requireNonNull(spec);
        unseenIds = new HashSet<>() {
            {
                addAll(List.of(specTxnIds));
            }
        };
        latch = new CountDownLatch(unseenIds.size());
    }

    public CompletableFuture<Map<String, List<RecordStreamEntry>>> entriesWithin(@NonNull final Duration timeout) {
        requireNonNull(timeout);
        return CompletableFuture.supplyAsync(() -> {
            try {
                latch.await(timeout.toMillis(), MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for all expected items!");
            }
            entries.values().forEach(entries -> entries.sort(Comparator.naturalOrder()));
            return entries;
        });
    }

    @Override
    public boolean isApplicableTo(@NonNull final RecordStreamItem item) {
        unseenIds.stream()
                .filter(id -> spec.registry()
                        .getMaybeTxnId(id)
                        .filter(txnId -> baseFieldsMatch(txnId, item.getRecord().getTransactionID()))
                        .isPresent())
                .findFirst()
                .ifPresentOrElse(
                        seenId -> {
                            log.info("Saw {} as {}", seenId, item.getRecord().getTransactionID());
                            entries.computeIfAbsent(seenId, ignore -> new ArrayList<>())
                                    .add(RecordStreamEntry.from(item));
                            if (!seenId.equals(lastSeenId)) {
                                maybeFinishLastSeen();
                            }
                            lastSeenId = seenId;
                        },
                        this::maybeFinishLastSeen);
        return true;
    }

    @Override
    public boolean test(@NonNull final RecordStreamItem item) throws AssertionError {
        return unseenIds.isEmpty();
    }

    private void maybeFinishLastSeen() {
        if (lastSeenId != null) {
            unseenIds.remove(lastSeenId);
            lastSeenId = null;
            latch.countDown();
        }
    }
}
