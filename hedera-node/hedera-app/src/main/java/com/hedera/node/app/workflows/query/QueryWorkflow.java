/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.hedera.hapi.node.transaction.Query;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/** A workflow for processing queries. */
public interface QueryWorkflow {

    /**
     * Called to handle a single query.
     *
     * @param requestBuffer The raw protobuf query bytes. Must be a {@link Query} object.
     * @param responseBuffer The raw protobuf response bytes.
     */
    void handleQuery(@NonNull Bytes requestBuffer, @NonNull BufferedData responseBuffer);
}
