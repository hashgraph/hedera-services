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
import com.hederahashgraph.api.proto.java.ExchangeRateSet;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExchangeRatesPojo {
    private RatePojo currentRate;
    private RatePojo nextRate;

    public static ExchangeRatesPojo fromProto(ExchangeRateSet proto) {
        var pojo = new ExchangeRatesPojo();
        pojo.setCurrentRate(RatePojo.fromProto(proto.getCurrentRate()));
        pojo.setNextRate(RatePojo.fromProto(proto.getNextRate()));
        return pojo;
    }

    public ExchangeRateSet toProto() {
        return ExchangeRateSet.newBuilder()
                .setCurrentRate(currentRate.toProto())
                .setNextRate(nextRate.toProto())
                .build();
    }

    public RatePojo getCurrentRate() {
        return currentRate;
    }

    public void setCurrentRate(RatePojo currentRate) {
        this.currentRate = currentRate;
    }

    public RatePojo getNextRate() {
        return nextRate;
    }

    public void setNextRate(RatePojo nextRate) {
        this.nextRate = nextRate;
    }
}
