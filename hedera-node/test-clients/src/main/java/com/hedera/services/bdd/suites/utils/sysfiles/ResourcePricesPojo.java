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
import com.hederahashgraph.api.proto.java.FeeComponents;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ResourcePricesPojo {
    public static ResourcePricesPojo from(FeeComponents prices) {
        var pojo = new ResourcePricesPojo();
        pojo.setBpr(prices.getBpr());
        pojo.setBpt(prices.getBpt());
        pojo.setConstant(prices.getConstant());
        pojo.setGas(prices.getGas());
        pojo.setMax(prices.getMax());
        pojo.setMin(prices.getMin());
        pojo.setRbh(prices.getRbh());
        pojo.setSbh(prices.getSbh());
        pojo.setSbpr(prices.getSbpr());
        pojo.setVpt(prices.getVpt());
        return pojo;
    }

    long constant, bpt, vpt, rbh, sbh, gas, bpr, sbpr, min, max;

    public long getConstant() {
        return constant;
    }

    public void setConstant(long constant) {
        this.constant = constant;
    }

    public long getBpt() {
        return bpt;
    }

    public void setBpt(long bpt) {
        this.bpt = bpt;
    }

    public long getVpt() {
        return vpt;
    }

    public void setVpt(long vpt) {
        this.vpt = vpt;
    }

    public long getRbh() {
        return rbh;
    }

    public void setRbh(long rbh) {
        this.rbh = rbh;
    }

    public long getSbh() {
        return sbh;
    }

    public void setSbh(long sbh) {
        this.sbh = sbh;
    }

    public long getGas() {
        return gas;
    }

    public void setGas(long gas) {
        this.gas = gas;
    }

    public long getBpr() {
        return bpr;
    }

    public void setBpr(long bpr) {
        this.bpr = bpr;
    }

    public long getSbpr() {
        return sbpr;
    }

    public void setSbpr(long sbpr) {
        this.sbpr = sbpr;
    }

    public long getMin() {
        return min;
    }

    public void setMin(long min) {
        this.min = min;
    }

    public long getMax() {
        return max;
    }

    public void setMax(long max) {
        this.max = max;
    }
}
