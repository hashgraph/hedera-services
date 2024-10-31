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

package com.hedera.node.app.service.schedule.impl.api;

import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.schedule.api.ScheduleServiceApi;
import com.hedera.node.app.spi.api.ServiceApiProvider;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

public enum ScheduleServiceApiProvider implements ServiceApiProvider<ScheduleServiceApi> {
    SCHEDULE_SERVICE_API_PROVIDER;

    @Override
    public String serviceName() {
        return ScheduleService.NAME;
    }

    @Override
    public ScheduleServiceApi newInstance(
            @NonNull Configuration configuration,
            @NonNull StoreMetricsService storeMetricsService,
            @NonNull WritableStates writableStates) {
        return new ScheduleServiceApiImpl(configuration, storeMetricsService, writableStates);
    }
}
