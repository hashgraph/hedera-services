/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.utils;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import java.util.Objects;

public class TokenExpiryWrapper {
    private final long second;
    private AccountID autoRenewAccount;
    private Duration autoRenewPeriod;

    public TokenExpiryWrapper(final long second, final AccountID autoRenewAccount, final Duration autoRenewPeriod) {
        this.second = second;
        this.autoRenewAccount = autoRenewAccount;
        this.autoRenewPeriod = autoRenewPeriod;
    }

    public long second() {
        return second;
    }

    public AccountID autoRenewAccount() {
        return autoRenewAccount;
    }

    public void setAutoRenewAccount(final AccountID autoRenewAccount) {
        this.autoRenewAccount = autoRenewAccount;
    }

    public Duration autoRenewPeriod() {
        return autoRenewPeriod;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TokenExpiryWrapper that = (TokenExpiryWrapper) o;
        return second == that.second
                && autoRenewPeriod == that.autoRenewPeriod
                && Objects.equals(autoRenewAccount, that.autoRenewAccount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(second, autoRenewAccount, autoRenewPeriod);
    }

    @Override
    public String toString() {
        return "TokenExpiryWrapper{"
                + "second="
                + second
                + ", autoRenewAccount="
                + autoRenewAccount
                + ", autoRenewPeriod="
                + autoRenewPeriod
                + '}';
    }
}
