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
package com.hedera.services.usage.token;

import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hedera.node.app.hapi.utils.fee.FeeBuilder.INT_SIZE;
import static com.hedera.services.usage.token.entities.NftEntitySizes.NFT_ENTITY_SIZES;

import com.google.protobuf.ByteString;
import com.hedera.services.usage.QueryUsage;
import com.hederahashgraph.api.proto.java.Query;
import java.util.List;

public class TokenGetNftInfosUsage extends QueryUsage {
    static final long INT_SIZE_AS_LONG = INT_SIZE;

    public TokenGetNftInfosUsage(Query query) {
        super(query.getTokenGetNftInfos().getHeader().getResponseType());
        addTb(BASIC_ENTITY_ID_SIZE + 2 * INT_SIZE_AS_LONG);
    }

    public static TokenGetNftInfosUsage newEstimate(Query query) {
        return new TokenGetNftInfosUsage(query);
    }

    public TokenGetNftInfosUsage givenMetadata(List<ByteString> metadata) {
        int additionalRb = 0;
        for (ByteString m : metadata) {
            additionalRb += m.size();
        }
        addRb(additionalRb);
        addRb(NFT_ENTITY_SIZES.fixedBytesInNftRepr() * metadata.size());

        return this;
    }
}
