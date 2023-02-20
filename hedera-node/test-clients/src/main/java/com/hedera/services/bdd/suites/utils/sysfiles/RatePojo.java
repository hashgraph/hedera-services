/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.TimestampSeconds;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class RatePojo {
    private int hbarEquiv;
    private int centEquiv;
    private long expiry;

    public static RatePojo fromProto(ExchangeRate rate) {
        var pojo = new RatePojo();
        pojo.setCentEquiv(rate.getCentEquiv());
        pojo.setHbarEquiv(rate.getHbarEquiv());
        pojo.setExpiry(rate.getExpirationTime().getSeconds());
        return pojo;
    }

    public ExchangeRate toProto() {
        return ExchangeRate.newBuilder()
                .setExpirationTime(TimestampSeconds.newBuilder().setSeconds(expiry))
                .setHbarEquiv(hbarEquiv)
                .setCentEquiv(centEquiv)
                .build();
    }

    public long getExpiry() {
        return expiry;
    }

    public void setExpiry(long expiry) {
        this.expiry = expiry;
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
}
