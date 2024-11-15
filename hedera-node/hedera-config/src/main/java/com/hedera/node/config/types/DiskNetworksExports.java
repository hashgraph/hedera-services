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
 * When to write network data to disk.
 */
public enum DiskNetworksExports {
    /**
     * Never write network data to disk.
     */
    NEVER,
    /**
     * Write network data to disk every time a state is saved.
     */
    EVERY_SAVED_STATE,
    /**
     * Write network data to disk only from the freeze state.
     */
    ONLY_FREEZE_STATE,
}
