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

package com.swirlds.common.system;

/**
 * Platform methods that have been deprecated.
 *
 * @deprecated this interface is not slated for long term support, do not add new dependencies on this interface
 */
@Deprecated(forRemoval = true)
public interface PlatformWithDeprecatedMethods extends Platform {

    /**
     * Get the latest mutable state. This method is not thread safe. use at your own risk.
     *
     * @deprecated this workflow is not thread safe
     */
    @Deprecated(forRemoval = true)
    <T extends SwirldState> T getState();

    /**
     * Should be called after {@link #getState()}.
     *
     * @deprecated this workflow is not thread safe
     */
    @Deprecated(forRemoval = true)
    void releaseState();
}
