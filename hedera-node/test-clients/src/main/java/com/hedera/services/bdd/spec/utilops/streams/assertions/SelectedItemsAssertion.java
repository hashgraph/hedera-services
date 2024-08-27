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

import com.hedera.node.app.hapi.utils.forensics.RecordStreamEntry;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.stream.proto.RecordStreamItem;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

/**
 * An assertion that validates a stream item against a predicate, and after finding an expected number of items,
 * validates them against a given {@link VisibleItemsValidator}.
 */
public class SelectedItemsAssertion implements RecordStreamAssertion {
    public static final String SELECTED_ITEMS_KEY = "SELECTED_ITEMS";

    private final int expectedCount;
    private final HapiSpec spec;
    private final BiPredicate<HapiSpec, RecordStreamItem> test;
    private final VisibleItemsValidator validator;
    private final List<RecordStreamEntry> selectedEntries = new ArrayList<>();

    public SelectedItemsAssertion(
            final int expectedCount,
            @NonNull final HapiSpec spec,
            @NonNull final BiPredicate<HapiSpec, RecordStreamItem> test,
            @NonNull final VisibleItemsValidator validator) {
        this.expectedCount = expectedCount;
        this.spec = spec;
        this.test = test;
        this.validator = validator;
    }

    @Override
    public boolean isApplicableTo(@NonNull final RecordStreamItem item) {
        return test.test(spec, item);
    }

    @Override
    public boolean test(@NonNull final RecordStreamItem item) throws AssertionError {
        final var entry = RecordStreamEntry.from(item);
        selectedEntries.add(entry);
        if (selectedEntries.size() == expectedCount) {
            validator.assertValid(
                    spec, Map.of(SELECTED_ITEMS_KEY, new VisibleItems(new AtomicInteger(), selectedEntries)));
            return true;
        }
        return false;
    }
}
