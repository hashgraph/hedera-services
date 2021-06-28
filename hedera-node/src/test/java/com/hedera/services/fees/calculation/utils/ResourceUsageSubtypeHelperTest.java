package com.hedera.services.fees.calculation.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.TokenType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Optional;

public class ResourceUsageSubtypeHelperTest {
    @Test
    public void determineTokenTypeWorksForDifferentTokens() {
        // given:
        Optional<TokenType> t1 = Optional.of(TokenType.FUNGIBLE_COMMON);
        Optional<TokenType> t2 = Optional.of(TokenType.NON_FUNGIBLE_UNIQUE);
        Optional<TokenType> t3 = Optional.of(TokenType.UNRECOGNIZED);

        // then:
        Assertions.assertEquals(SubType.TOKEN_FUNGIBLE_COMMON, ResourceUsageSubtypeHelper.determineTokenType(t1));
        Assertions.assertEquals(SubType.TOKEN_NON_FUNGIBLE_UNIQUE, ResourceUsageSubtypeHelper.determineTokenType(t2));
        Assertions.assertEquals(SubType.DEFAULT, ResourceUsageSubtypeHelper.determineTokenType(t3));
    }
}
