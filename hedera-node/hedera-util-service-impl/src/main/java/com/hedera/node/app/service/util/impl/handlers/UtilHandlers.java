/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.util.impl.handlers;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class UtilHandlers {

    private final UtilPrngHandler prngHandler;
    private final AtomicBatchHandler atomicBatchHandler;

    @Inject
    public UtilHandlers(
            @NonNull final UtilPrngHandler prngHandler, @NonNull final AtomicBatchHandler atomicBatchHandler) {
        this.prngHandler = Objects.requireNonNull(prngHandler, "prngHandler must not be null");
        this.atomicBatchHandler = Objects.requireNonNull(atomicBatchHandler, "atomicBatchHandler must not be null");
    }

    public UtilPrngHandler prngHandler() {
        return prngHandler;
    }

    public AtomicBatchHandler atomicBatchHandler() {
        return atomicBatchHandler;
    }
}
