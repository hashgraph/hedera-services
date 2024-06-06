/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.node.app.service.schedule.impl.handlers.ScheduleCreateHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleDeleteHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleGetInfoHandler;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleHandlers;
import com.hedera.node.app.service.schedule.impl.handlers.ScheduleSignHandler;
import dagger.Module;

/**
 * Schedule service injection interface. Used to inject the schedule service handlers into the
 * implementation class using Dagger dependency injection
 */
@Module
public interface ScheduleServiceInjectionModule {

    /**
     * Schedule create handler
     *
     * @return the schedule create handler
     */
    ScheduleCreateHandler scheduleCreateHandler();

    /**
     * Schedule delete handler
     *
     * @return the schedule delete handler
     */
    ScheduleDeleteHandler scheduleDeleteHandler();

    /**
     * Schedule get info handler
     *
     * @return the schedule get info handler
     */
    ScheduleGetInfoHandler scheduleGetInfoHandler();

    /**
     * Schedule sign handler
     *
     * @return the schedule sign handler
     */
    ScheduleSignHandler scheduleSignHandler();

    /**
     * Schedule handlers
     *
     * @return the schedule handlers
     */
    ScheduleHandlers scheduleHandlers();
}
