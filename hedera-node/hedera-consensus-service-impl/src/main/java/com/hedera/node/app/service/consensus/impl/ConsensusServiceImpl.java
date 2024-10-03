/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.consensus.impl;

import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.consensus.impl.schemas.V0490ConsensusSchema;
import com.hedera.node.app.spi.RpcService;
import com.swirlds.state.spi.SchemaRegistry;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Standard implementation of the {@link ConsensusService} {@link RpcService}.
 */
public final class ConsensusServiceImpl implements ConsensusService {
    /**
     * Topic running hash
     */
    public static final int RUNNING_HASH_BYTE_ARRAY_SIZE = 48;

    /**
     * Topics state key
     */
    public static final String TOPICS_KEY = "TOPICS";

    @Override
    public void registerSchemas(@NonNull SchemaRegistry registry) {
        registry.register(new V0490ConsensusSchema());
    }
}
