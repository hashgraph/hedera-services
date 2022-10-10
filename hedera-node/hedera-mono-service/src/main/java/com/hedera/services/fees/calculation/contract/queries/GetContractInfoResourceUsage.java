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
package com.hedera.services.fees.calculation.contract.queries;

import static com.hedera.services.queries.contract.GetContractInfoAnswer.CONTRACT_INFO_CTX_KEY;
import static com.hedera.services.utils.MiscUtils.putIfNotNull;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.fees.calculation.QueryResourceUsageEstimator;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.staking.RewardCalculator;
import com.hedera.services.usage.contract.ContractGetInfoUsage;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetContractInfoResourceUsage implements QueryResourceUsageEstimator {
    private static final Function<Query, ContractGetInfoUsage> factory =
            ContractGetInfoUsage::newEstimate;

    private final AliasManager aliasManager;
    private final GlobalDynamicProperties dynamicProperties;
    private final RewardCalculator rewardCalculator;

    @Inject
    public GetContractInfoResourceUsage(
            AliasManager aliasManager,
            GlobalDynamicProperties dynamicProperties,
            final RewardCalculator rewardCalculator) {
        this.aliasManager = aliasManager;
        this.dynamicProperties = dynamicProperties;
        this.rewardCalculator = rewardCalculator;
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasContractGetInfo();
    }

    @Override
    public FeeData usageGiven(
            final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getContractGetInfo();
        final var tentativeInfo =
                view.infoForContract(
                        op.getContractID(),
                        aliasManager,
                        dynamicProperties.maxTokensRelsPerInfoQuery(),
                        rewardCalculator);
        if (tentativeInfo.isPresent()) {
            final var info = tentativeInfo.get();
            putIfNotNull(queryCtx, CONTRACT_INFO_CTX_KEY, info);
            final var estimate =
                    factory.apply(query)
                            .givenCurrentKey(info.getAdminKey())
                            .givenCurrentMemo(info.getMemo())
                            .givenCurrentTokenAssocs(info.getTokenRelationshipsCount());
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }
}
