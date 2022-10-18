/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files.sysfiles;

import com.hedera.services.fees.FeeMultiplierSource;
import com.hedera.services.throttling.FunctionalityThrottling;
import com.hedera.services.throttling.annotations.HandleThrottle;
import com.hedera.services.throttling.annotations.HapiThrottle;
import com.hedera.services.throttling.annotations.ScheduleThrottle;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ThrottlesCallback {
    private final FeeMultiplierSource multiplierSource;
    private final FunctionalityThrottling hapiThrottling;
    private final FunctionalityThrottling handleThrottling;
    private final FunctionalityThrottling scheduleThrottling;

    @Inject
    public ThrottlesCallback(
            FeeMultiplierSource multiplierSource,
            @HapiThrottle FunctionalityThrottling hapiThrottling,
            @HandleThrottle FunctionalityThrottling handleThrottling,
            @ScheduleThrottle FunctionalityThrottling scheduleThrottling) {
        this.multiplierSource = multiplierSource;
        this.hapiThrottling = hapiThrottling;
        this.handleThrottling = handleThrottling;
        this.scheduleThrottling = scheduleThrottling;
    }

    public Consumer<ThrottleDefinitions> throttlesCb() {
        return throttles -> {
            var defs =
                    com.hedera.services.sysfiles.domain.throttling.ThrottleDefinitions.fromProto(
                            throttles);
            hapiThrottling.rebuildFor(defs);
            handleThrottling.rebuildFor(defs);
            scheduleThrottling.rebuildFor(defs);
            multiplierSource.resetExpectations();
        };
    }
}
