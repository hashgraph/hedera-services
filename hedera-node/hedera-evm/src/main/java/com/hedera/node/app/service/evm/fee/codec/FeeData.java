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

public class FeeData {

    FeeComponents nodeData;
    FeeComponents networkData;
    FeeComponents serviceData;
    SubType subType;

    public FeeData(FeeComponents nodeData, FeeComponents networkData, FeeComponents serviceData, SubType subType) {
        this.nodeData = nodeData;
        this.networkData = networkData;
        this.serviceData = serviceData;
        this.subType = subType;
    }

    public FeeComponents getNodeData() {
        return nodeData;
    }

    public void setNodeData(FeeComponents nodeData) {
        this.nodeData = nodeData;
    }

    public FeeComponents getNetworkData() {
        return networkData;
    }

    public void setNetworkData(FeeComponents networkData) {
        this.networkData = networkData;
    }

    public FeeComponents getServiceData() {
        return serviceData;
    }

    public void setServiceData(FeeComponents serviceData) {
        this.serviceData = serviceData;
    }

    public SubType getSubType() {
        return subType;
    }

    public void setSubType(SubType subType) {
        this.subType = subType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        FeeData feeData = (FeeData) o;
        return Objects.equals(nodeData, feeData.nodeData)
                && Objects.equals(networkData, feeData.networkData)
                && Objects.equals(serviceData, feeData.serviceData)
                && subType == feeData.subType;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeData, networkData, serviceData, subType);
    }

    @Override
    public String toString() {
        return "FeeData{" + "nodeData="
                + nodeData + ", networkData="
                + networkData + ", serviceData="
                + serviceData + ", subType="
                + subType + '}';
    }
}
