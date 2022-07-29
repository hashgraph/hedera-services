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

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenAnswers {
    private final GetTokenInfoAnswer tokenInfo;
    private final GetTokenNftInfoAnswer nftInfo;
    private final GetTokenNftInfosAnswer tokenNftInfos;
    private final GetAccountNftInfosAnswer accountNftInfos;

    @Inject
    public TokenAnswers(
            GetTokenInfoAnswer tokenInfo,
            GetTokenNftInfoAnswer nftInfo,
            GetTokenNftInfosAnswer tokenNftInfos,
            GetAccountNftInfosAnswer accountNftInfos) {
        this.tokenInfo = tokenInfo;
        this.nftInfo = nftInfo;
        this.tokenNftInfos = tokenNftInfos;
        this.accountNftInfos = accountNftInfos;
    }

    public GetTokenInfoAnswer getTokenInfo() {
        return tokenInfo;
    }

    public GetTokenNftInfoAnswer getNftInfoAnswer() {
        return nftInfo;
    }

    public GetTokenNftInfosAnswer getTokenNftInfosAnswer() {
        return tokenNftInfos;
    }

    public GetAccountNftInfosAnswer getAccountNftInfosAnswer() {
        return accountNftInfos;
    }
}
