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

package com.hedera.node.app.state.recordcache;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * A service representing this record cache facility. Many services are "big S" services like Hedera Consensus Service.
 * Facilities like Record Cache are "little s" services. They have state and schemas, but are not big ticket services
 * marketed to the world.
 */
public class RecordCacheService implements Service {
    /** The record cache service name */
    public static final String NAME = "RecordCache";
    /** The name of the queue that stores the transaction records */
    static final String TXN_RECORD_QUEUE = "TransactionRecordQueue";

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    /** {@inheritDoc} */
    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        // This is the genesis schema for this service, and simply creates the queue state that stores the
        // transaction records.
        registry.register(new Schema(SemanticVersion.newBuilder().minor(38).build()) {
            @NonNull
            @Override
            public SemanticVersion getVersion() {
                return super.getVersion();
            }

            @NonNull
            @Override
            @SuppressWarnings("rawtypes")
            public Set<StateDefinition> statesToCreate() {
                return Set.of(StateDefinition.queue(TXN_RECORD_QUEUE, TransactionRecordEntry.PROTOBUF));
            }
        });
    }
}
