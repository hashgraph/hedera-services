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

package com.hedera.node.app.service.evm.fee.codec;

import java.util.Objects;

public class ExchangeRate {

    int hbarEquiv;
    int centEquiv;
    long expirationTime;

    public ExchangeRate() {}

    public ExchangeRate(int hbarEquiv, int centEquiv, long expirationTime) {
        this.hbarEquiv = hbarEquiv;
        this.centEquiv = centEquiv;
        this.expirationTime = expirationTime;
    }

    public int getHbarEquiv() {
        return hbarEquiv;
    }

    public void setHbarEquiv(int hbarEquiv) {
        this.hbarEquiv = hbarEquiv;
    }

    public int getCentEquiv() {
        return centEquiv;
    }

    public void setCentEquiv(int centEquiv) {
        this.centEquiv = centEquiv;
    }

    public long getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(long expirationTime) {
        this.expirationTime = expirationTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        ExchangeRate that = (ExchangeRate) o;
        return hbarEquiv == that.hbarEquiv && centEquiv == that.centEquiv && expirationTime == that.expirationTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(hbarEquiv, centEquiv, expirationTime);
    }

    @Override
    public String toString() {
        return "ExchangeRate{" + "hbarEquiv="
                + hbarEquiv + ", centEquiv="
                + centEquiv + ", expirationTime="
                + expirationTime + '}';
    }
}
