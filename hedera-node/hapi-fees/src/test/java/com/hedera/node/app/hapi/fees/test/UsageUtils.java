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
package com.hedera.node.app.hapi.fees.test;

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;

public class UsageUtils {
    public static final int NUM_PAYER_KEYS = 2;

    public static final long ONE = 1;
    public static final long BPT = 2;
    public static final long VPT = 3;
    public static final long RBH = 4;
    public static final long SBH = 5;
    public static final long GAS = 6;
    public static final long TV = 7;
    public static final long BPR = 8;
    public static final long SBPR = 9;
    public static final long NETWORK_RBH = 10;

    public static final FeeComponents A_USAGE_VECTOR =
            FeeComponents.newBuilder()
                    .setConstant(ONE)
                    .setBpt(BPT)
                    .setVpt(VPT)
                    .setRbh(RBH)
                    .setSbh(SBH)
                    .setGas(GAS)
                    .setTv(TV)
                    .setBpr(BPR)
                    .setSbpr(SBPR)
                    .build();

    public static final FeeData A_USAGES_MATRIX;
    public static final FeeData A_QUERY_USAGES_MATRIX;

    static {
        var usagesBuilder = FeeData.newBuilder();
        usagesBuilder.setNetworkdata(
                FeeComponents.newBuilder()
                        .setConstant(ONE)
                        .setBpt(BPT)
                        .setVpt(VPT)
                        .setRbh(NETWORK_RBH));
        usagesBuilder.setNodedata(
                FeeComponents.newBuilder()
                        .setConstant(ONE)
                        .setBpt(BPT)
                        .setVpt(NUM_PAYER_KEYS)
                        .setBpr(BPR)
                        .setSbpr(SBPR));
        usagesBuilder.setServicedata(
                FeeComponents.newBuilder().setConstant(ONE).setRbh(RBH).setSbh(SBH).setTv(TV));
        A_USAGES_MATRIX = usagesBuilder.build();

        usagesBuilder = FeeData.newBuilder();
        usagesBuilder.setNodedata(
                FeeComponents.newBuilder().setConstant(ONE).setBpt(BPT).setSbpr(SBPR).setBpr(BPR));
        A_QUERY_USAGES_MATRIX = usagesBuilder.build();
    }
}
