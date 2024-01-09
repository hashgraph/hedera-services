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

package com.hedera.node.app.service.schedule.impl.handlers;

import static java.util.Objects.requireNonNull;

import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ScheduleHandlers {

    private final ScheduleCreateHandler scheduleCreateHandler;

    private final ScheduleDeleteHandler scheduleDeleteHandler;

    private final ScheduleGetInfoHandler scheduleGetInfoHandler;

    private final ScheduleSignHandler scheduleSignHandler;

    @Inject
    public ScheduleHandlers(
            @NonNull final ScheduleCreateHandler scheduleCreateHandler,
            @NonNull final ScheduleDeleteHandler scheduleDeleteHandler,
            @NonNull final ScheduleGetInfoHandler scheduleGetInfoHandler,
            @NonNull final ScheduleSignHandler scheduleSignHandler) {
        this.scheduleCreateHandler = requireNonNull(scheduleCreateHandler, "scheduleCreateHandler must not be null");
        this.scheduleDeleteHandler = requireNonNull(scheduleDeleteHandler, "scheduleDeleteHandler must not be null");
        this.scheduleGetInfoHandler = requireNonNull(scheduleGetInfoHandler, "scheduleGetInfoHandler must not be null");
        this.scheduleSignHandler = requireNonNull(scheduleSignHandler, "scheduleSignHandler must not be null");
    }

    public ScheduleCreateHandler scheduleCreateHandler() {
        return scheduleCreateHandler;
    }

    public ScheduleDeleteHandler scheduleDeleteHandler() {
        return scheduleDeleteHandler;
    }

    public ScheduleGetInfoHandler scheduleGetInfoHandler() {
        return scheduleGetInfoHandler;
    }

    public ScheduleSignHandler scheduleSignHandler() {
        return scheduleSignHandler;
    }
}
