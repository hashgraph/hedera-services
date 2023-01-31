/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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

import com.hedera.node.app.SessionContext;
import java.nio.ByteBuffer;
import javax.annotation.Nonnull;

/**
 * Dummy implementation. To be implemented by <a
 * href="https://github.com/hashgraph/hedera-services/issues/4209">#4209</a>.
 */
public final class IngestWorkflowImpl implements IngestWorkflow {
    @Override
    public void handleTransaction(
            @Nonnull final SessionContext session,
            @Nonnull final ByteBuffer requestBuffer,
            @Nonnull final ByteBuffer responseBuffer) {
        // Implementation to be completed by Issue #4209
    }
}
