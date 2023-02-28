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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SCHEDULE_IS_IMMUTABLE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.schedule.impl.ReadableScheduleStore;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleDelete}.
 */
@Singleton
public class ScheduleDeleteHandler implements TransactionHandler {
    @Inject
    public ScheduleDeleteHandler() {}

    /**
     * This method is called during the pre-handle workflow.
     *
     * <p>Pre-handles a {@link
     * com.hederahashgraph.api.proto.java.HederaFunctionality#ScheduleDelete} transaction, returning
     * the metadata required to, at minimum, validate the signatures of all required signing keys.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param context the {@link PreHandleContext} which collects all information that will be
     *     passed to {@link #handle(TransactionMetadata)}
     * @param scheduleStore the {@link ReadableScheduleStore} that contains all scheduled-data
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void preHandle(@NonNull final PreHandleContext context, @NonNull final ReadableScheduleStore scheduleStore) {
        requireNonNull(context);
        final var op = context.getTxn().getScheduleDelete();
        final var id = op.getScheduleID();

        // check for a missing schedule. A schedule with this id could have never existed,
        // or it could have already been executed or deleted
        final var scheduleLookupResult = scheduleStore.get(id);
        if (scheduleLookupResult.isEmpty()) {
            context.status(INVALID_SCHEDULE_ID);
            return;
        }

        // No need to check for SCHEDULE_PENDING_EXPIRATION, SCHEDULE_ALREADY_DELETED,
        // SCHEDULE_ALREADY_EXECUTED
        // if any of these are the case then the scheduled tx would not be present in scheduleStore

        // check whether schedule was created with an admin key
        // if it wasn't, the schedule can't be deleted
        final var adminKey = scheduleLookupResult.get().adminKey();
        if (adminKey.isEmpty()) {
            context.status(SCHEDULE_IS_IMMUTABLE);
            return;
        }

        // add admin key of the original ScheduleCreate tx
        // to the list of keys required to execute this ScheduleDelete tx
        context.addToReqNonPayerKeys(adminKey.get());
    }

    /**
     * This method is called during the handle workflow. It executes the actual transaction.
     *
     * <p>Please note: the method signature is just a placeholder which is most likely going to
     * change.
     *
     * @param metadata the {@link TransactionMetadata} that was generated during pre-handle.
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public void handle(@NonNull final TransactionMetadata metadata) {
        requireNonNull(metadata);
        throw new UnsupportedOperationException("Not implemented");
    }
}
