/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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

import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;

/**
 * A helper class which maps model enums {@link TokenType} and {@link TokenSupplyType} to {@link
 * com.hederahashgraph.api.proto.java.TokenType} and {@link
 * com.hederahashgraph.api.proto.java.TokenSupplyType} This helper class can be extended to do
 * reverse mapping if ever needed.
 *
 * @author Yoan Sredkov (yoansredkov@gmail.com)
 */
public final class TokenTypesMapper {
    public static TokenType mapToDomain(
            final com.hederahashgraph.api.proto.java.TokenType grpcType) {
        if (grpcType == com.hederahashgraph.api.proto.java.TokenType.NON_FUNGIBLE_UNIQUE) {
            return TokenType.NON_FUNGIBLE_UNIQUE;
        }
        return TokenType.FUNGIBLE_COMMON;
    }

    public static TokenSupplyType mapToDomain(
            final com.hederahashgraph.api.proto.java.TokenSupplyType grpcType) {
        if (grpcType == com.hederahashgraph.api.proto.java.TokenSupplyType.FINITE) {
            return TokenSupplyType.FINITE;
        }
        return TokenSupplyType.INFINITE;
    }

    private TokenTypesMapper() {
        throw new UnsupportedOperationException("Utility class");
    }
}
