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

import static com.hedera.node.app.hapi.fees.usage.token.entities.NftEntitySizes.NFT_ENTITY_SIZES;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;

import com.google.protobuf.ByteString;
import com.hedera.node.app.hapi.fees.usage.QueryUsage;
import com.hederahashgraph.api.proto.java.Query;
import java.util.List;

public class TokenGetAccountNftInfosUsage extends QueryUsage {
    private static final long INT_SIZE_AS_LONG = INT_SIZE;

    public TokenGetAccountNftInfosUsage(final Query query) {
        super(query.getTokenGetAccountNftInfos().getHeader().getResponseType());
        addTb(BASIC_ENTITY_ID_SIZE);
        addTb(2 * INT_SIZE_AS_LONG);
    }

    public static TokenGetAccountNftInfosUsage newEstimate(final Query query) {
        return new TokenGetAccountNftInfosUsage(query);
    }

    public TokenGetAccountNftInfosUsage givenMetadata(final List<ByteString> metadata) {
        int additionalRb = 0;
        for (final ByteString m : metadata) {
            additionalRb += m.size();
        }
        addRb(additionalRb);
        addRb(NFT_ENTITY_SIZES.fixedBytesInNftRepr() * metadata.size());
        return this;
    }
}
