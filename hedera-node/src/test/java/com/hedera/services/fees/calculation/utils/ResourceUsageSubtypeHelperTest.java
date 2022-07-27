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
package com.hedera.services.fees.calculation.utils;

import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.TokenType;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ResourceUsageSubtypeHelperTest {
    private ResourceUsageSubtypeHelper subject = new ResourceUsageSubtypeHelper();

    @Test
    void emptyOptionalIsFungibleCommon() {
        // expect:
        assertEquals(TOKEN_FUNGIBLE_COMMON, subject.determineTokenType(Optional.empty()));
    }

    @Test
    void presentValuesAreAsExpected() {
        // expect:
        assertEquals(
                TOKEN_FUNGIBLE_COMMON,
                subject.determineTokenType(Optional.of(TokenType.UNRECOGNIZED)));
        assertEquals(
                TOKEN_FUNGIBLE_COMMON,
                subject.determineTokenType(Optional.of(TokenType.FUNGIBLE_COMMON)));
        assertEquals(
                TOKEN_NON_FUNGIBLE_UNIQUE,
                subject.determineTokenType(Optional.of(TokenType.NON_FUNGIBLE_UNIQUE)));
    }
}
