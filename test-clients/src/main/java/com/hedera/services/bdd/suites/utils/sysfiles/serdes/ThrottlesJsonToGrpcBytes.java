/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;

public class ThrottlesJsonToGrpcBytes implements SysFileSerde<String> {
    private static final int MINIMUM_NETWORK_SIZE = 1;

    private int believedNetworkSize;
    private final ObjectMapper mapper = new ObjectMapper();

    public ThrottlesJsonToGrpcBytes() {
        this.believedNetworkSize = MINIMUM_NETWORK_SIZE;
    }

    public void setBelievedNetworkSize(int believedNetworkSize) {
        this.believedNetworkSize = believedNetworkSize;
    }

    public ThrottlesJsonToGrpcBytes(int believedNetworkSize) {
        this.believedNetworkSize = believedNetworkSize;
    }

    @Override
    public String fromRawFile(byte[] bytes) {
        try {
            var defs = ThrottleDefinitions.parseFrom(bytes);
            var pojo =
                    com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions
                            .fromProto(defs);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pojo);
        } catch (InvalidProtocolBufferException | JsonProcessingException e) {
            throw new IllegalArgumentException("Unusable raw throttle definitions!", e);
        }
    }

    @Override
    public byte[] toRawFile(String styledFile) {
        return toPojo(styledFile).toProto().toByteArray();
    }

    @Override
    public byte[] toValidatedRawFile(String styledFile) {
        var pojo = toPojo(styledFile);
        for (var bucket : pojo.getBuckets()) {
            bucket.asThrottleMapping(believedNetworkSize);
        }
        return pojo.toProto().toByteArray();
    }

    private com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions toPojo(
            String styledFile) {
        try {
            return mapper.readValue(
                    styledFile,
                    com.hedera.node.app.hapi.utils.sysfiles.domain.throttling.ThrottleDefinitions
                            .class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Unusable styled throttle definitions", e);
        }
    }

    @Override
    public String preferredFileName() {
        return "throttles.json";
    }
}
