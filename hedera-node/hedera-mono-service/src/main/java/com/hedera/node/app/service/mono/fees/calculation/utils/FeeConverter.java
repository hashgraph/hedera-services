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

package com.hedera.node.app.service.mono.fees.calculation.utils;

import com.hedera.node.app.service.evm.utils.codec.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TimestampSeconds;

public class FeeConverter {

    private FeeConverter() {
        // Utility class
    }

    public static FeeData convertFeeDataFromDtoToProto(
            final com.hedera.node.app.service.evm.fee.codec.FeeData feeData) {
        if (feeData != null) {
            final var networkData = feeData.getNetworkData();
            final var serviceData = feeData.getServiceData();
            final var nodeData = feeData.getNodeData();

            final var networkDataProto = networkData != null
                    ? convertFeeComponentsDtoToProto(networkData)
                    : FeeComponents.getDefaultInstance();
            final var serviceDataProto = serviceData != null
                    ? convertFeeComponentsDtoToProto(serviceData)
                    : FeeComponents.getDefaultInstance();
            final var nodeDataProto =
                    nodeData != null ? convertFeeComponentsDtoToProto(nodeData) : FeeComponents.getDefaultInstance();
            return FeeData.newBuilder()
                    .setNetworkdata(networkDataProto)
                    .setServicedata(serviceDataProto)
                    .setNodedata(nodeDataProto)
                    .build();
        } else {
            return FeeData.getDefaultInstance();
        }
    }

    public static com.hedera.node.app.service.evm.fee.codec.FeeData convertFeeDataFromProtoToDto(
            final FeeData feeDataProto) {
        final var protoNodeData = feeDataProto.getNodedata();
        final var protoNetworkData = feeDataProto.getNetworkdata();
        final var protoServiceData = feeDataProto.getServicedata();

        final var nodeData = convertFeeComponentsFromProtoToDto(protoNodeData);
        final var networkData = convertFeeComponentsFromProtoToDto(protoNetworkData);
        final var serviceData = convertFeeComponentsFromProtoToDto(protoServiceData);
        return new com.hedera.node.app.service.evm.fee.codec.FeeData(
                nodeData,
                networkData,
                serviceData,
                com.hedera.node.app.service.evm.fee.codec.SubType.valueOf(
                        feeDataProto.getSubType().name()));
    }

    public static com.hedera.node.app.service.evm.fee.codec.ExchangeRate convertExchangeRateFromProtoToDto(
            final com.hederahashgraph.api.proto.java.ExchangeRate exchangeRate) {
        return new com.hedera.node.app.service.evm.fee.codec.ExchangeRate(
                exchangeRate.getHbarEquiv(),
                exchangeRate.getCentEquiv(),
                exchangeRate.getExpirationTime().getSeconds());
    }

    private static com.hedera.node.app.service.evm.fee.codec.FeeComponents convertFeeComponentsFromProtoToDto(
            com.hederahashgraph.api.proto.java.FeeComponents protoFeeComponents) {
        return new com.hedera.node.app.service.evm.fee.codec.FeeComponents(
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

    public static FeeComponents convertFeeComponentsDtoToProto(
            final com.hedera.node.app.service.evm.fee.codec.FeeComponents feeComponents) {
        return FeeComponents.newBuilder()
                .setBpr(feeComponents.getBpr())
                .setBpt(feeComponents.getBpt())
                .setConstant(feeComponents.getConstant())
                .setGas(feeComponents.getGas())
                .setRbh(feeComponents.getRbh())
                .setSbh(feeComponents.getSbh())
                .setSbpr(feeComponents.getSbpr())
                .setMax(feeComponents.getMax())
                .setMin(feeComponents.getMin())
                .setTv(feeComponents.getTv())
                .setVpt(feeComponents.getVpt())
                .build();
    }

    public static ExchangeRate convertExchangeRateFromDtoToProto(
            final com.hedera.node.app.service.evm.fee.codec.ExchangeRate exchangeRate) {
        return ExchangeRate.newBuilder()
                .setCentEquiv(exchangeRate.getCentEquiv())
                .setHbarEquiv(exchangeRate.getHbarEquiv())
                .setExpirationTime(TimestampSeconds.newBuilder()
                        .setSeconds(exchangeRate.getExpirationTime())
                        .build())
                .build();
    }

    public static com.hedera.node.app.service.evm.utils.codec.Timestamp convertTimestampFromProtoToDto(
            final Timestamp timestamp) {
        return new com.hedera.node.app.service.evm.utils.codec.Timestamp(timestamp.getSeconds(), timestamp.getNanos());
    }

    public static HederaFunctionality convertHederaFunctionalityFromProtoToDto(
            final com.hederahashgraph.api.proto.java.HederaFunctionality hederaFunctionality) {
        return HederaFunctionality.valueOf(hederaFunctionality.name());
    }
}
