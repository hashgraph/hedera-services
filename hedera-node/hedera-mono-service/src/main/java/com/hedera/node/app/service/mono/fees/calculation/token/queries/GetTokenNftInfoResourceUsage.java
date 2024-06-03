/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.fees.calculation.token.queries;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.fromPbj;
import static com.hedera.node.app.service.mono.queries.token.GetTokenNftInfoAnswer.NFT_INFO_CTX_KEY;
import static com.hedera.node.app.service.mono.utils.MiscUtils.putIfNotNull;
import static com.hedera.node.app.spi.fees.Fees.CONSTANT_FEE_DATA;

import com.hedera.hapi.node.state.token.Nft;
import com.hedera.node.app.hapi.fees.usage.token.TokenGetNftInfoUsage;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.calculation.QueryResourceUsageEstimator;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Query;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Map;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public final class GetTokenNftInfoResourceUsage implements QueryResourceUsageEstimator {
    private static final Function<Query, TokenGetNftInfoUsage> factory = TokenGetNftInfoUsage::newEstimate;

    @Inject
    public GetTokenNftInfoResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final Query query) {
        return query.hasTokenGetNftInfo();
    }

    @Override
    public FeeData usageGiven(final Query query, final StateView view, @Nullable final Map<String, Object> queryCtx) {
        final var op = query.getTokenGetNftInfo();
        final var optionalInfo = view.infoForNft(op.getNftID());
        if (optionalInfo.isPresent()) {
            final var info = optionalInfo.get();
            putIfNotNull(queryCtx, NFT_INFO_CTX_KEY, info);
            final var estimate =
                    factory.apply(query).givenMetadata(info.getMetadata().toString());
            return estimate.get();
        } else {
            return FeeData.getDefaultInstance();
        }
    }

    /**
     * This method is used to calculate the fee for the {@code GetNftInfo}
     * only in modularized code, until new fee logic is implemented.
     * @param query query to be processed
     * @param nft nft whose info to be retrieved
     * @return fee data
     */
    public FeeData usageGiven(final com.hedera.hapi.node.transaction.Query query, final Nft nft) {
        if (nft != null) {
            final var estimate =
                    factory.apply(fromPbj(query)).givenMetadata(nft.metadata().toString());
            return estimate.get();
        } else {
            return CONSTANT_FEE_DATA;
        }
    }
}
