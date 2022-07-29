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
package com.hedera.services.queries.crypto;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class CryptoAnswers {
    private final GetLiveHashAnswer liveHash;
    private final GetStakersAnswer stakers;
    private final GetAccountInfoAnswer accountInfo;
    private final GetAccountBalanceAnswer accountBalance;
    private final GetAccountRecordsAnswer accountRecords;

    @Inject
    public CryptoAnswers(
            final GetLiveHashAnswer liveHash,
            final GetStakersAnswer stakers,
            final GetAccountInfoAnswer accountInfo,
            final GetAccountBalanceAnswer accountBalance,
            final GetAccountRecordsAnswer accountRecords) {
        this.liveHash = liveHash;
        this.stakers = stakers;
        this.accountInfo = accountInfo;
        this.accountBalance = accountBalance;
        this.accountRecords = accountRecords;
    }

    public GetLiveHashAnswer getLiveHash() {
        return liveHash;
    }

    public GetStakersAnswer getStakers() {
        return stakers;
    }

    public GetAccountBalanceAnswer getAccountBalance() {
        return accountBalance;
    }

    public GetAccountInfoAnswer getAccountInfo() {
        return accountInfo;
    }

    public GetAccountRecordsAnswer getAccountRecords() {
        return accountRecords;
    }
}
