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

import com.hederahashgraph.api.proto.java.HederaFunctionality;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Placeholder; once https://github.com/hashgraph/hedera-services/pull/4800 is polished and merged,
 * it should be straightforward to implement this class in terms of {@code mono-service} components
 * wired via Dagger.
 */
@Singleton
public class MonoThrottleAccumulator implements ThrottleAccumulator {
    @Inject
    public MonoThrottleAccumulator() {}

    @Override
    public boolean shouldThrottle(@NonNull HederaFunctionality functionality) {
        return false;
    }
}
