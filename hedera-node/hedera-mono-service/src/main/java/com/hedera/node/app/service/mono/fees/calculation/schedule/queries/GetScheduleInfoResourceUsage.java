/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.schedule.queries;

import static com.hedera.node.app.service.mono.queries.schedule.GetScheduleInfoAnswer.SCHEDULE_INFO_CTX_KEY;
import static com.hedera.node.app.service.mono.utils.MiscUtils.putIfNotNull;

import com.hedera.node.app.hapi.fees.usage.schedule.ExtantScheduleContext;
import com.hedera.node.app.hapi.fees.usage.schedule.ScheduleOpsUsage;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ScheduleInfo;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
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
    public FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getScheduleGetInfo();
        final var optionalInfo = view.infoForSchedule(op.getScheduleID());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            putIfNotNull(queryCtx, SCHEDULE_INFO_CTX_KEY, info);
            final var scheduleCtxBuilder = ExtantScheduleContext.newBuilder()
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

    public FeeData usageGiven(final Query query, final ScheduleInfo info) {
        if (info != null) {
            final var scheduleCtxBuilder = ExtantScheduleContext.newBuilder()
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
