/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.records;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.snapshotMode;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMode.FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS;
import static com.hedera.services.bdd.spec.utilops.records.SnapshotMode.TAKE_FROM_HAPI_TEST_STREAMS;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An operation that delegates to a {@link SnapshotModeOp} depending on whether the currently executing
 * {@link HapiSpec} has a record snapshot already saved.
 * <ul>
 *     <Li>If the snapshot already exists, inserts a {@code snapshotMode(FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS)}.
 *     <Li>If the snapshot does not exist, inserts a {@code snapshotMode(TAKE_FROM_HAPI_TEST_STREAMS)}.
 * </ul>
 */
public class AutoSnapshotModeOp extends UtilOp implements SnapshotOp {
    private SnapshotModeOp delegate;
    private final SnapshotMatchMode[] snapshotMatchModes;

    public static @Nullable SnapshotOp from(@NonNull final HapiSpec spec) {
        final var setup = spec.setup();
        if (setup.autoSnapshotManagement()) {
            return new AutoSnapshotModeOp(spec.getSnapshotMatchModes());
        } else {
            return null;
        }
    }

    public AutoSnapshotModeOp(@NonNull final SnapshotMatchMode[] snapshotMatchModes) {
        this.snapshotMatchModes = snapshotMatchModes;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var maybeSnapshot = SnapshotModeOp.maybeLoadSnapshotFor(spec);
        if (maybeSnapshot.isPresent() && !spec.setup().overrideExistingSnapshot()) {
            delegate = snapshotMode(FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS, snapshotMatchModes);
        } else {
            delegate = snapshotMode(TAKE_FROM_HAPI_TEST_STREAMS, snapshotMatchModes);
        }
        return delegate.submitOp(spec);
    }

    @Override
    public boolean hasWorkToDo() {
        return requireNonNull(delegate).hasWorkToDo();
    }

    @Override
    public void finishLifecycle(@NonNull final HapiSpec spec) {
        requireNonNull(delegate).finishLifecycle(spec);
    }
}
