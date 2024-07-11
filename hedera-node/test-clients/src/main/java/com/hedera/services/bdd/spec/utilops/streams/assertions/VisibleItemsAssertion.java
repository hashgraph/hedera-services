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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
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
    private static final long FIRST_USER_NUM = 1001L;

    static final Logger log = LogManager.getLogger(VisibleItemsAssertion.class);

    private final HapiSpec spec;
    private final Set<String> unseenIds;
    private final CountDownLatch latch;
    private final Map<String, List<RecordStreamEntry>> entries = new HashMap<>();
    private final boolean withLogging = true;

    @Nullable
    private String lastSeenId = null;

    private final SkipSynthItems skipSynthItems;

    public enum SkipSynthItems {
        YES,
        NO
    }

    public VisibleItemsAssertion(
            @NonNull final HapiSpec spec,
            @NonNull final SkipSynthItems skipSynthItems,
            @NonNull final String... specTxnIds) {
        this.spec = requireNonNull(spec);
        this.skipSynthItems = requireNonNull(skipSynthItems);
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
                            final var entry = RecordStreamEntry.from(item);
                            if (skipSynthItems == SkipSynthItems.NO || !isSynthItem(entry)) {
                                if (withLogging) {
                                    log.info(
                                            "Saw {} as {}",
                                            seenId,
                                            item.getRecord().getTransactionID());
                                }
                                entries.computeIfAbsent(seenId, ignore -> new ArrayList<>())
                                        .add(entry);
                                if (!seenId.equals(lastSeenId)) {
                                    maybeFinishLastSeen();
                                }
                                lastSeenId = seenId;
                            }
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

    private static boolean isSynthItem(@NonNull final RecordStreamEntry entry) {
        final var receipt = entry.transactionRecord().getReceipt();
        return entry.function() == NodeStakeUpdate
                || (receipt.getAccountID().hasAccountNum()
                        && receipt.getAccountID().getAccountNum() < FIRST_USER_NUM)
                || (receipt.hasFileID() && receipt.getFileID().getFileNum() < FIRST_USER_NUM);
    }
}
