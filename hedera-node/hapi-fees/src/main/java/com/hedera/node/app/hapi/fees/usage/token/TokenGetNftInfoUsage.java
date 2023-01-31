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
package com.hedera.node.app.hapi.fees.usage.token;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.LONG_SIZE;

import com.hedera.node.app.hapi.fees.usage.QueryUsage;
import com.hedera.node.app.hapi.fees.usage.token.entities.NftEntitySizes;
import com.hederahashgraph.api.proto.java.Query;

public class TokenGetNftInfoUsage extends QueryUsage {
    public TokenGetNftInfoUsage(final Query query) {
        super(query.getTokenGetNftInfo().getHeader().getResponseType());
        addTb(BASIC_ENTITY_ID_SIZE);
        addTb(LONG_SIZE);
        addRb(NftEntitySizes.NFT_ENTITY_SIZES.fixedBytesInNftRepr());
    }

    public static TokenGetNftInfoUsage newEstimate(final Query query) {
        return new TokenGetNftInfoUsage(query);
    }

    public TokenGetNftInfoUsage givenMetadata(final String memo) {
        addRb(memo.length());
        return this;
    }
}
