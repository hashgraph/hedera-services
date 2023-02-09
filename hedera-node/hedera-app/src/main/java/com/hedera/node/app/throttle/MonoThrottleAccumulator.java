/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.throttle;

import com.hedera.node.app.service.mono.throttling.FunctionalityThrottling;
import com.hedera.node.app.service.mono.throttling.annotations.HapiThrottle;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A {@link ThrottleAccumulator} that delegates to a {@link FunctionalityThrottling} instance
 * to support query throttling only.
 */
@Singleton
public class MonoThrottleAccumulator implements ThrottleAccumulator {
    private final FunctionalityThrottling hapiThrottling;

    @Inject
    public MonoThrottleAccumulator(@HapiThrottle final FunctionalityThrottling hapiThrottling) {
        this.hapiThrottling = hapiThrottling;
    }

    @Override
    public boolean shouldThrottle(@NonNull HederaFunctionality functionality) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean shouldThrottleQuery(
            final @NonNull HederaFunctionality functionality, final @NonNull Query query) {
        return hapiThrottling.shouldThrottleQuery(functionality, query);
    }
}
