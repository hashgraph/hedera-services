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
import static com.hedera.services.bdd.spec.utilops.streams.assertions.VisibleItems.newVisibleItems;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class VisibleItemsAssertion implements RecordStreamAssertion {
    private static final long FIRST_USER_NUM = 1001L;

    private static final Logger log = LogManager.getLogger(VisibleItemsAssertion.class);

    private final HapiSpec spec;
    private final Set<String> unseenIds;
    private final VisibleItemsValidator validator;
    private final Map<String, VisibleItems> items = new ConcurrentHashMap<>();
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
            @NonNull final VisibleItemsValidator validator,
            @NonNull final SkipSynthItems skipSynthItems,
            @NonNull final String... specTxnIds) {
        this.spec = requireNonNull(spec);
        this.validator = validator;
        this.skipSynthItems = requireNonNull(skipSynthItems);
        unseenIds = new HashSet<>() {
            {
                addAll(List.of(specTxnIds));
            }
        };
    }

    @Override
    public String toString() {
        return "VisibleItemsAssertion{" + "unseenIds=" + unseenIds + ", seenIds=" + items.keySet() + '}';
    }

    @Override
    public synchronized boolean isApplicableTo(@NonNull final RecordStreamItem item) {
        new ArrayList<>(unseenIds)
                .stream()
                        .filter(id -> spec.registry()
                                .getMaybeTxnId(id)
                                .filter(txnId ->
                                        baseFieldsMatch(txnId, item.getRecord().getTransactionID()))
                                .isPresent())
                        .findFirst()
                        .ifPresentOrElse(
                                seenId -> {
                                    final var entry = RecordStreamEntry.from(item);
                                    final var isSynthItem = isSynthItem(entry);
                                    if (skipSynthItems == SkipSynthItems.NO || !isSynthItem) {
                                        if (withLogging) {
                                            log.info(
                                                    "Saw {} as {}",
                                                    seenId,
                                                    item.getRecord().getTransactionID());
                                        }
                                        items.computeIfAbsent(seenId, ignore -> newVisibleItems())
                                                .entries()
                                                .add(entry);
                                        if (!seenId.equals(lastSeenId)) {
                                            maybeFinishLastSeen();
                                        }
                                        lastSeenId = seenId;
                                    } else {
                                        items.computeIfAbsent(seenId, ignore -> newVisibleItems())
                                                .trackSkippedSynthItem();
                                    }
                                },
                                this::maybeFinishLastSeen);
        return true;
    }

    @Override
    public boolean test(@NonNull final RecordStreamItem item) throws AssertionError {
        if (unseenIds.isEmpty()) {
            validator.assertValid(spec, items);
            return true;
        }
        return false;
    }

    private void maybeFinishLastSeen() {
        if (lastSeenId != null) {
            unseenIds.remove(lastSeenId);
            lastSeenId = null;
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
