/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss;

import com.swirlds.state.lifecycle.Service;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * The service for the inexact weight TSS implementation to be used before completion of exact-weight TSS
 * scheme. This service is now responsible only for registering schemas to deserialize, and then remove,
 * the states added in {@code 0.56.0} and {@code 0.58.0}.
 */
@Deprecated(forRemoval = true, since = "0.59.0")
public interface TssBaseService extends Service {
    String NAME = "TssBaseService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }
}
