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

package com.hedera.node.app.service.mono.fees.calculation;

import com.hedera.node.app.service.evm.fee.FeeResourcesLoader;
import com.hedera.node.app.service.evm.fee.codec.FeeComponents;
import com.hedera.node.app.service.evm.fee.codec.FeeData;
import com.hedera.node.app.service.evm.fee.codec.SubType;
import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hedera.node.app.service.evm.utils.codec.Timestamp;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;

public class FeeResourcesLoaderImpl implements FeeResourcesLoader {

    private final BasicFcfsUsagePrices basicFcfsUsagePrices;

    @Inject
    public FeeResourcesLoaderImpl(final BasicFcfsUsagePrices basicFcfsUsagePrices) {
        this.basicFcfsUsagePrices = basicFcfsUsagePrices;
    }

    @Override
    public Timestamp currFunctionUsagePricesExpiry() {
        return convertTimestampProtoToDto(basicFcfsUsagePrices.getCurrFunctionUsagePricesExpiry());
    }

    @Override
    public Timestamp nextFunctionUsagePricesExpiry() {
        return convertTimestampProtoToDto(basicFcfsUsagePrices.getNextFunctionUsagePricesExpiry());
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> currFunctionUsagePrices() {
        final var currentFunctionUsagePrices = basicFcfsUsagePrices.getCurrFunctionUsagePrices();

        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> convertedCurrentFunctionUsagePrices =
                new EnumMap<>(com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.class);
        Arrays.stream(HederaFunctionality.values())
                .forEach(f -> convertedCurrentFunctionUsagePrices.put(
                        HederaFunctionality.valueOf(f.toString()),
                        convertSubTypeToFeeDataMapFromProtoToDto(currentFunctionUsagePrices.get(
                                com.hederahashgraph.api.proto.java.HederaFunctionality.valueOf(f.toString())))));

        return convertedCurrentFunctionUsagePrices;
    }

    @Override
    public EnumMap<HederaFunctionality, Map<SubType, FeeData>> nextFunctionUsagePrices() {
        final var nextFunctionUsagePrices = basicFcfsUsagePrices.getNextFunctionUsagePrices();

        final EnumMap<HederaFunctionality, Map<SubType, FeeData>> convertedNextFunctionUsagePrices =
                new EnumMap<>(com.hedera.node.app.service.evm.utils.codec.HederaFunctionality.class);
        Arrays.stream(HederaFunctionality.values())
                .forEach(f -> convertedNextFunctionUsagePrices.put(
                        HederaFunctionality.valueOf(f.toString()),
                        convertSubTypeToFeeDataMapFromProtoToDto(nextFunctionUsagePrices.get(
                                com.hederahashgraph.api.proto.java.HederaFunctionality.valueOf(f.toString())))));

        return convertedNextFunctionUsagePrices;
    }

    private Map<SubType, FeeData> convertSubTypeToFeeDataMapFromProtoToDto(
            final Map<com.hederahashgraph.api.proto.java.SubType, com.hederahashgraph.api.proto.java.FeeData> map) {
        final var subTypeMap = new HashMap<SubType, FeeData>();

        for (final var entry : map.entrySet()) {
            final var protoFeeData = entry.getValue();
            final var protoNodeData = protoFeeData.getNodedata();
            final var protoNetworkData = protoFeeData.getNetworkdata();
            final var protoServiceData = protoFeeData.getServicedata();

            final var nodeData = convertFeeComponentsFromProtoToDto(protoNodeData);
            final var networkData = convertFeeComponentsFromProtoToDto(protoNetworkData);
            final var serviceData = convertFeeComponentsFromProtoToDto(protoServiceData);
            final var feeData = new FeeData(
                    nodeData,
                    networkData,
                    serviceData,
                    SubType.valueOf(protoFeeData.getSubType().name()));

            subTypeMap.put(SubType.valueOf(entry.getKey().name()), feeData);
        }

        return subTypeMap;
    }

    public FeeComponents convertFeeComponentsFromProtoToDto(
            com.hederahashgraph.api.proto.java.FeeComponents protoFeeComponents) {
        return new FeeComponents(
                protoFeeComponents.getMin(),
                protoFeeComponents.getMax(),
                protoFeeComponents.getConstant(),
                protoFeeComponents.getBpt(),
                protoFeeComponents.getVpt(),
                protoFeeComponents.getRbh(),
                protoFeeComponents.getSbh(),
                protoFeeComponents.getGas(),
                protoFeeComponents.getTv(),
                protoFeeComponents.getBpr(),
                protoFeeComponents.getSbpr());
    }

    private Timestamp convertTimestampProtoToDto(com.hederahashgraph.api.proto.java.Timestamp timestamp) {
        return new Timestamp(timestamp.getSeconds(), timestamp.getNanos());
    }
}
