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
package com.hedera.services.fees.calculation.utils;

import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Optional;

/**
 * A helper class used for determining the subtype of the token, involved in fee calculation.
 * Defaulted to `TOKEN_FUNGIBLE_COMMON` unless its a `NON_FUNGIBLE_UNIQUE` token
 */
public class ResourceUsageSubtypeHelper {
    public SubType determineTokenType(Optional<TokenType> tokenType) {
        if (tokenType.isPresent() && tokenType.get() == TokenType.NON_FUNGIBLE_UNIQUE) {
            return SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
        }
        return SubType.TOKEN_FUNGIBLE_COMMON;
    }
}
