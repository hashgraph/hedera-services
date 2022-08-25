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
package com.hedera.services.config;

public class MockAccountNumbers extends AccountNumbers {
    public MockAccountNumbers() {
        super(null);
    }

    @Override
    public long treasury() {
        return 2;
    }

    @Override
    public long systemAdmin() {
        return 50;
    }

    @Override
    public long addressBookAdmin() {
        return 55;
    }

    @Override
    public long feeSchedulesAdmin() {
        return 56;
    }

    @Override
    public long exchangeRatesAdmin() {
        return 57;
    }

    @Override
    public long freezeAdmin() {
        return 58;
    }

    @Override
    public long systemDeleteAdmin() {
        return 59;
    }

    @Override
    public long systemUndeleteAdmin() {
        return 60;
    }

    @Override
    public boolean isSuperuser(long num) {
        return (num == 2) || (num == 50);
    }

    @Override
    public long stakingRewardAccount() {
        return 800L;
    }

    @Override
    public long nodeRewardAccount() {
        return 801L;
    }
}
