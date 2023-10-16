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

package com.hedera.node.app.spi.fixtures.throttle;

import com.hedera.hapi.node.transaction.ThrottleDefinitions;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.hapi.utils.throttles.GasLimitDeterministicThrottle;
import com.hedera.node.app.spi.throttle.HandleThrottleParser;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collections;
import java.util.List;

public class FakeHandleThrottleParser implements HandleThrottleParser {
    @Override
    public void rebuildFor(@NonNull ThrottleDefinitions defs) {
        // Intentional no-op
    }

    @Override
    public void applyGasConfig() {
        // Intentional no-op
    }

    @NonNull
    @Override
    public List<DeterministicThrottle> allActiveThrottles() {
        return Collections.emptyList();
    }

    @Nullable
    @Override
    public GasLimitDeterministicThrottle gasLimitThrottle() {
        return null;
    }
}
