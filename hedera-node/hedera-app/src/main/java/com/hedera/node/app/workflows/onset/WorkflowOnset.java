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
package com.hedera.node.app.workflows.onset;

import com.hedera.node.app.SessionContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;

// TODO: Implemented as part of ingest workflow, needs to be merged
public class WorkflowOnset {

    public OnsetResult parseAndCheck(
            @NonNull final SessionContext ctx, @NonNull final byte[] buffer)
            throws PreCheckException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
