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

package com.hedera.node.app.state.recordcache.schemas;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.swirlds.state.merkle.MigrationContext;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.merkle.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

public class V0490RecordCacheSchema extends Schema {
    /** The name of the queue that stores the transaction records */
    public static final String TXN_RECORD_QUEUE = "TransactionRecordQueue";
    /**
     * The version of the schema.
     */
    private static final SemanticVersion VERSION =
            SemanticVersion.newBuilder().major(0).minor(49).patch(0).build();

    public V0490RecordCacheSchema() {
        super(VERSION);
    }

    @NonNull
    @Override
    @SuppressWarnings("rawtypes")
    public Set<StateDefinition> statesToCreate() {
        return Set.of(StateDefinition.queue(V0490RecordCacheSchema.TXN_RECORD_QUEUE, TransactionRecordEntry.PROTOBUF));
    }

    @Override
    public void migrate(@NonNull final MigrationContext ctx) {
        // No genesis records
    }
}
