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

package com.hedera.node.app.workflows.ingest;

import com.hedera.node.app.state.HederaState;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Supplier;

/**
 * Module for Ingest processing.
 */
@Module
public interface IngestModule {
    @Binds
    IngestWorkflow bindIngestWorkflow(IngestWorkflowImpl ingestWorkflow);

    @Provides
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Supplier<AutoCloseableWrapper<HederaState>> provideStateSupplier(@NonNull final Platform platform) {
        // Always return the latest immutable state until we support state proofs
        return () -> (AutoCloseableWrapper) platform.getLatestImmutableState();
    }
}
