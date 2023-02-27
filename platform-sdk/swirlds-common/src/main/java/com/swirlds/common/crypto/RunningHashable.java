/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.crypto;

/**
 * Each RunningHashable instance contains a RunningHash instance, which encapsulates a Hash object
 * which denotes a running Hash calculated from all RunningHashable in history up to this RunningHashable instance
 */
public interface RunningHashable extends Hashable {

    /**
     * Gets the current {@link RunningHash} instance associated with this object. This method should always return an
     * instance of the {@link RunningHash} class and should never return a {@code null} value.
     *
     * @return the attached {@code RunningHash} instance.
     */
    RunningHash getRunningHash();
}
