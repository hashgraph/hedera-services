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
package com.hedera.services.fees.calculation.consensus.queries;

import static com.hedera.services.utils.EntityNum.fromTopicId;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hederahashgraph.fee.ConsensusServiceFeeBuilder.computeVariableSizedFieldsUsage;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TX_HASH_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.getQueryFeeDataMatrices;
import static com.hederahashgraph.fee.FeeBuilder.getStateProofSize;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetTopicInfoResourceUsage implements QueryResourceUsageEstimator {
    @Inject
    public GetTopicInfoResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasConsensusGetTopicInfo();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, final Map<String, Object> ignoreCtx) {
        return usageGivenType(
                query, view, query.getConsensusGetTopicInfo().getHeader().getResponseType());
    }

    @Override
    public FeeData usageGivenType(
            final Query query, final StateView view, final ResponseType responseType) {
        final var merkleTopic =
                view.topics().get(fromTopicId(query.getConsensusGetTopicInfo().getTopicID()));

        if (merkleTopic == null) {
            return FeeData.getDefaultInstance();
        }

        final long bpr =
                BASIC_QUERY_RES_HEADER
                        + getStateProofSize(responseType)
                        + BASIC_ENTITY_ID_SIZE
                        + getTopicInfoSize(merkleTopic);
        final var feeMatrices =
                FeeComponents.newBuilder()
                        .setBpt(BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE)
                        .setVpt(0)
                        .setRbh(0)
                        .setSbh(0)
                        .setGas(0)
                        .setTv(0)
                        .setBpr(bpr)
                        .setSbpr(0)
                        .build();
        return getQueryFeeDataMatrices(feeMatrices);
    }

    private static int getTopicInfoSize(final MerkleTopic merkleTopic) {
        /* Three longs in a topic representation: sequenceNumber, expirationTime, autoRenewPeriod */
        return TX_HASH_SIZE
                + 3 * LONG_SIZE
                + computeVariableSizedFieldsUsage(
                        asKeyUnchecked(merkleTopic.getAdminKey()),
                        asKeyUnchecked(merkleTopic.getSubmitKey()),
                        merkleTopic.getMemo(),
                        merkleTopic.hasAutoRenewAccountId());
    }
}
