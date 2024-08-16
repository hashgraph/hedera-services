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

package com.hedera.node.app.state.recordcache;

import com.hedera.node.app.state.recordcache.schemas.V0490RecordCacheSchema;
import com.hedera.node.app.state.recordcache.schemas.V0540RecordCacheSchema;
import com.swirlds.state.spi.SchemaRegistry;
import com.swirlds.state.spi.Service;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A service representing this record cache facility. Many services are "big S" services like Hedera Consensus Service.
 * Facilities like Record Cache are "little s" services. They have state and schemas, but are not big ticket services
 * marketed to the world.
 */
public class RecordCacheService implements Service {
    /** The record cache service name */
    public static final String NAME = "RecordCache";

    /** {@inheritDoc} */
    @NonNull
    @Override
    public String getServiceName() {
        return NAME;
    }

    /** {@inheritDoc} */
    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry) {
        registry.register(new V0490RecordCacheSchema());
        registry.register(new V0540RecordCacheSchema());
    }
}
