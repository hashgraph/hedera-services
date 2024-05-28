/*
<<<<<<<< HEAD:hedera-node/hedera-schedule-service/src/main/java/com/hedera/node/app/service/schedule/ScheduleService.java
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
========
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
>>>>>>>> main:modules/hedera-schedule-service/src/main/java/com/hedera/node/app/service/scheduled/ScheduleService.java
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
<<<<<<<< HEAD:hedera-node/hedera-schedule-service/src/main/java/com/hedera/node/app/service/schedule/ScheduleService.java

package com.hedera.node.app.service.schedule;
========
package com.hedera.node.app.service.scheduled;
>>>>>>>> main:modules/hedera-schedule-service/src/main/java/com/hedera/node/app/service/scheduled/ScheduleService.java

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
<<<<<<<< HEAD:hedera-node/hedera-schedule-service/src/main/java/com/hedera/node/app/service/schedule/ScheduleService.java
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
import java.util.Set;
========
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ServiceLoader;
>>>>>>>> main:modules/hedera-schedule-service/src/main/java/com/hedera/node/app/service/scheduled/ScheduleService.java

/**
 * Implements the HAPI <a
 * href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/schedule_service.proto">Schedule
 * Service</a>.
 */
public interface ScheduleService extends Service {
<<<<<<<< HEAD:hedera-node/hedera-schedule-service/src/main/java/com/hedera/node/app/service/schedule/ScheduleService.java

    String NAME = "ScheduleService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }

    @NonNull
    @Override
    default Set<RpcServiceDefinition> rpcDefinitions() {
        return Set.of(ScheduleServiceDefinition.INSTANCE);
    }
========
    /**
     * Creates the schedule service pre-handler given a particular Hedera world state.
     *
     * @param states the state of the world
     * @return the corresponding schedule service pre-handler
     */
    @NonNull
    @Override
    SchedulePreTransactionHandler createPreTransactionHandler(
            @NonNull States states, @NonNull PreHandleContext ctx);
>>>>>>>> main:modules/hedera-schedule-service/src/main/java/com/hedera/node/app/service/scheduled/ScheduleService.java

    /**
     * Returns the concrete implementation instance of the service
     *
     * @return the implementation instance
     */
    @NonNull
    static ScheduleService getInstance() {
<<<<<<<< HEAD:hedera-node/hedera-schedule-service/src/main/java/com/hedera/node/app/service/schedule/ScheduleService.java
        return ServiceFactory.loadService(ScheduleService.class, ServiceLoader.load(ScheduleService.class));
========
        return ServiceFactory.loadService(
                ScheduleService.class, ServiceLoader.load(ScheduleService.class));
>>>>>>>> main:modules/hedera-schedule-service/src/main/java/com/hedera/node/app/service/scheduled/ScheduleService.java
    }
}
