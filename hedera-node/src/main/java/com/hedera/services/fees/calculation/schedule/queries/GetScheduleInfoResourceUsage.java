/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation.schedule.queries;

import static com.hedera.services.queries.schedule.GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.usage.schedule.ExtantScheduleContext;
import com.hedera.services.usage.schedule.ScheduleOpsUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import java.util.Map;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetScheduleInfoResourceUsage implements QueryResourceUsageEstimator {
    private final ScheduleOpsUsage scheduleOpsUsage;

    @Inject
    public GetScheduleInfoResourceUsage(final ScheduleOpsUsage scheduleOpsUsage) {
        this.scheduleOpsUsage = scheduleOpsUsage;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasScheduleGetInfo();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getScheduleGetInfo();
        final var optionalInfo = view.infoForSchedule(op.getScheduleID());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            putIfNotNull(queryCtx, SCHEDULE_INFO_CTX_KEY, info);
            final var scheduleCtxBuilder =
                    ExtantScheduleContext.newBuilder()
                            .setScheduledTxn(info.getScheduledTransactionBody())
                            .setMemo(info.getMemo())
                            .setNumSigners(info.getSigners().getKeysCount())
                            .setResolved(info.hasExecutionTime() || info.hasDeletionTime());
            if (info.hasAdminKey()) {
                scheduleCtxBuilder.setAdminKey(info.getAdminKey());
            } else {
                scheduleCtxBuilder.setNoAdminKey();
            }
            return scheduleOpsUsage.scheduleInfoUsage(query, scheduleCtxBuilder.build());
        } else {
            return FeeData.getDefaultInstance();
        }
    }
}
