/*
 * Copyright (C) 2020-2021 Hedera Hashgraph, LLC
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

import static com.hedera.services.usage.token.entities.NftEntitySizes.NFT_ENTITY_SIZES;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_ENTITY_ID_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.BASIC_QUERY_RES_HEADER;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.test.IdUtils;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.TokenGetNftInfoQuery;
import com.hederahashgraph.fee.FeeBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenGetNftInfoUsageTest {
    private String memo = "Hope";
    private NftID id = IdUtils.asNftID("0.0.75231", 1);

    private TokenGetNftInfoUsage subject;

    @BeforeEach
    void setup() {
        subject = TokenGetNftInfoUsage.newEstimate(query());
    }

    @Test
    void assessesEverything() {
        // given:
        subject.givenMetadata(memo);
        // and:
        var expectedBytes =
                BASIC_QUERY_RES_HEADER + NFT_ENTITY_SIZES.fixedBytesInNftRepr() + memo.length();

        // when:
        var usage = subject.get();

        // then:
        var node = usage.getNodedata();

        assertEquals(
                FeeBuilder.BASIC_QUERY_HEADER + BASIC_ENTITY_ID_SIZE + LONG_SIZE, node.getBpt());
        assertEquals(expectedBytes, node.getBpr());
    }

    private Query query() {
        var op = TokenGetNftInfoQuery.newBuilder().setNftID(id).build();
        return Query.newBuilder().setTokenGetNftInfo(op).build();
    }
}
