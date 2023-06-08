/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static com.hedera.node.app.spi.key.KeyUtils.isEmpty;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.node.app.service.schedule.ReadableScheduleStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#SCHEDULE_DELETE}.
 */
@Singleton
public class ScheduleDeleteHandler implements TransactionHandler {
    @Inject
    public ScheduleDeleteHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().scheduleDeleteOrThrow();
        final var id = op.scheduleIDOrElse(ScheduleID.DEFAULT);
        final var scheduleStore = context.createStore(ReadableScheduleStore.class);

        // check for a missing schedule. A schedule with this id could have never existed,
        // or it could have already been executed or deleted
        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            throw new PreCheckException(INVALID_SCHEDULE_ID);
        }

        // No need to check for SCHEDULE_PENDING_EXPIRATION, SCHEDULE_ALREADY_DELETED,
        // SCHEDULE_ALREADY_EXECUTED
        // if any of these are the case then the scheduled tx would not be present in scheduleStore

        // check whether schedule was created with an admin key
        // if it wasn't, the schedule can't be deleted
        final var adminKey = scheduleLookupResult.get().adminKey();
        if (isEmpty(adminKey)) {
            throw new PreCheckException(SCHEDULE_IS_IMMUTABLE);
        }

        // add admin key of the original ScheduleCreate tx
        // to the list of keys required to execute this ScheduleDelete tx
        context.requireKey(adminKey);
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
