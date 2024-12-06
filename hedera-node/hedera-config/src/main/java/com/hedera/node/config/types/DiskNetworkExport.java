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

package com.hedera.node.config.types;

/**
 * Whether and how network metadata should be exported to disk for use in a later network transplant.
 */
public enum DiskNetworkExport {
    /**
     * Never export network metadata to disk.
     */
    NEVER,
    /**
     * Export network metadata to disk every block, convenient for getting a file as quickly as possible in a
     * development network that will serve as the target of a state transplant from a production network.
     */
    EVERY_BLOCK,
    /**
     * Export network metadata to disk only when the network is frozen.
     */
    ONLY_FREEZE_BLOCK,
}
