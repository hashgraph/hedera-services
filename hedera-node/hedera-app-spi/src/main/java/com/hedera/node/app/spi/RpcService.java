/*
 * Copyright (C) 2022-2025 Hedera Hashgraph, LLC
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
import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * This interface defines the contract for a service that can expose RPC endpoints.
 */
public interface RpcService extends Service {

    /**
     * If this service exposes RPC endpoints, then this method returns the RPC service definitions.
     *
     * @return The RPC service definitions if this service is exposed via RPC.
     */
    @NonNull
    Set<RpcServiceDefinition> rpcDefinitions();

    /**
     * Services may have initialization to be done which can't be done in the constructor (too soon)
     * but should/must be done before the system starts processing transactions. This is the hook
     * for that.
     *
     * Called on each Service when `Hedera.onStateInitialized() is called for `InitTrigger.GENESIS`.
     * Services module is still single-threaded when this happens.
     *
     * N.B.: Each service must take care about what's done at this point: It must lead to a
     * deterministic state and deterministic block stream.
     */
    default void onStateInitializedForGenesis() {}
}
