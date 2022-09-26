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
package com.hedera.services.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import org.junit.jupiter.api.Test;

class TokenTypesMapperTest {
    @Test
    void grpcTokenTypeToModelType() {
        assertEquals(
                TokenType.FUNGIBLE_COMMON,
                TokenTypesMapper.mapToDomain(
                        com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON));
        assertEquals(
                TokenType.NON_FUNGIBLE_UNIQUE,
                TokenTypesMapper.mapToDomain(
                        com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE));
    }

    @Test
    void grpcTokenSupplyTypeToModelSupplyType() {
        assertEquals(
                TokenSupplyType.FINITE,
                TokenTypesMapper.mapToDomain(
                        com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE));

        assertEquals(
                TokenSupplyType.INFINITE,
                TokenTypesMapper.mapToDomain(
                        com.hederahashgraph.api.proto.java.TokenSupplyType.INFINITE));

        /* ensure default is infinite */
        assertEquals(
                TokenSupplyType.INFINITE,
                TokenTypesMapper.mapToDomain(
                        com.hederahashgraph.api.proto.java.TokenSupplyType.UNRECOGNIZED));
    }
}
