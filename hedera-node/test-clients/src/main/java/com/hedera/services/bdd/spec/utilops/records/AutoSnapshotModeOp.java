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

package com.hedera.services.bdd.spec.utilops.records;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.snapshotMode;
import static com.hedera.services.bdd.spec.utilops.records.AutoSnapshotRecordSource.MONO_SERVICE;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * An operation that delegates to a {@link SnapshotModeOp} depending on whether the currently executing
 * {@link HapiSpec} has a record snapshot already saved.
 * <ul>
 *     <Li>If the snapshot already exists, inserts either a {@code snapshotMode(FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS)}
 *     or {@code snapshotMode(FUZZY_MATCH_AGAINST_MONO_STREAMS)} depending on the given {@link AutoSnapshotRecordSource}.</Li>
 *     <Li>If the snapshot does not exist, inserts either a {@code snapshotMode(FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS)}
 *     or {@code snapshotMode(FUZZY_MATCH_AGAINST_MONO_STREAMS)} depending on the given {@link AutoSnapshotRecordSource}.</Li>
 * </ul>
 */
public class AutoSnapshotModeOp extends UtilOp implements SnapshotOp {
    private final AutoSnapshotRecordSource autoTakeSource;
    private final AutoSnapshotRecordSource autoMatchSource;

    private SnapshotModeOp delegate;

    public static @Nullable SnapshotOp from(@NonNull final HapiSpecSetup setup) {
        if (setup.autoSnapshotManagement()) {
            return new AutoSnapshotModeOp(setup.autoSnapshotTarget(), setup.autoMatchTarget());
        } else {
            return null;
        }
    }

    public AutoSnapshotModeOp(
            @NonNull final AutoSnapshotRecordSource autoTakeSource,
            @NonNull final AutoSnapshotRecordSource autoMatchSource) {
        this.autoTakeSource = autoTakeSource;
        this.autoMatchSource = autoMatchSource;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var maybeSnapshot = SnapshotModeOp.maybeLoadSnapshotFor(spec);
        if (maybeSnapshot.isPresent()) {
            final var snapshotMode = (autoMatchSource == MONO_SERVICE)
                    ? SnapshotMode.FUZZY_MATCH_AGAINST_MONO_STREAMS
                    : SnapshotMode.FUZZY_MATCH_AGAINST_HAPI_TEST_STREAMS;
            delegate = snapshotMode(snapshotMode);
        } else {
            final var snapshotMode = (autoTakeSource == MONO_SERVICE)
                    ? SnapshotMode.TAKE_FROM_MONO_STREAMS
                    : SnapshotMode.TAKE_FROM_HAPI_TEST_STREAMS;
            delegate = snapshotMode(snapshotMode);
        }
        return delegate.submitOp(spec);
    }

    @Override
    public boolean hasWorkToDo() {
        return requireNonNull(delegate).hasWorkToDo();
    }

    @Override
    public void finishLifecycle() {
        requireNonNull(delegate).finishLifecycle();
    }
}
