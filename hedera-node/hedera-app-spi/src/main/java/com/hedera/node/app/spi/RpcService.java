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

package com.hedera.node.app.spi;

import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * This interface defines the contract for a service that can expose RPC endpoints.
 */
// todo fix!
public interface RpcService extends MetricsService {

    /**
     * If this service exposes RPC endpoints, then this method returns the RPC service definitions.
     *
     * @return The RPC service definitions if this service is exposed via RPC.
     */
    @NonNull
    Set<RpcServiceDefinition> rpcDefinitions();
}
