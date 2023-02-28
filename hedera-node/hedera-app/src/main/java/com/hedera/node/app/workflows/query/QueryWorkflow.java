/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.query;

import com.hedera.node.app.SessionContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

/** A workflow for processing queries. */
public interface QueryWorkflow {

    /**
     * Called to handle a single query.
     *
     * @param session The per-request {@link SessionContext}.
     * @param requestBuffer The raw protobuf query bytes. Must be a {@link com.hederahashgraph.api.proto.java.Query}
     * object.
     * @param responseBuffer The raw protobuf response bytes.
     */
    void handleQuery(
            @NonNull SessionContext session, @NonNull ByteBuffer requestBuffer, @NonNull ByteBuffer responseBuffer);
}
