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
package com.hedera.services.queries.token;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenAnswersTest {
    GetTokenInfoAnswer tokenInfo;
    GetTokenNftInfoAnswer nftInfo;
    GetTokenNftInfosAnswer tokenNftsInfo;
    GetAccountNftInfosAnswer accountNftInfos;

    @BeforeEach
    void setup() {
        tokenInfo = mock(GetTokenInfoAnswer.class);
        nftInfo = mock(GetTokenNftInfoAnswer.class);
        tokenNftsInfo = mock(GetTokenNftInfosAnswer.class);
        accountNftInfos = mock(GetAccountNftInfosAnswer.class);
    }

    @Test
    void getsQueryBalance() {
        // given:
        TokenAnswers subject = new TokenAnswers(tokenInfo, nftInfo, tokenNftsInfo, accountNftInfos);

        // expect:
        assertSame(tokenInfo, subject.getTokenInfo());
        assertSame(nftInfo, subject.getNftInfoAnswer());
        assertSame(tokenNftsInfo, subject.getTokenNftInfosAnswer());
        assertSame(accountNftInfos, subject.getAccountNftInfosAnswer());
    }
}
