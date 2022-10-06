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
package com.hedera.services.context.domain.trackers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ConsensusStatusCounts {
    private static final Logger log = LogManager.getLogger(ConsensusStatusCounts.class);

    private final ObjectMapper om;
    EnumMap<ResponseCodeEnum, EnumMap<HederaFunctionality, AtomicInteger>> counts =
            new EnumMap<>(ResponseCodeEnum.class);

    public ConsensusStatusCounts(ObjectMapper om) {
        this.om = om;
    }

    public String asJson() {
        var asList =
                counts.entrySet().stream()
                        .flatMap(
                                entries ->
                                        entries.getValue().entrySet().stream()
                                                .map(
                                                        entry ->
                                                                Map.of(
                                                                        String.format(
                                                                                "%s:%s",
                                                                                entries.getKey(),
                                                                                entry.getKey()),
                                                                        entry.getValue().get())))
                        .toList();
        try {
            return om.writerWithDefaultPrettyPrinter().writeValueAsString(asList);
        } catch (JsonProcessingException unlikely) {
            log.warn("Unable to serialize status counts!", unlikely);
            return "[ ]";
        }
    }

    public void increment(HederaFunctionality op, ResponseCodeEnum status) {
        counts.computeIfAbsent(status, ignore -> new EnumMap<>(HederaFunctionality.class))
                .computeIfAbsent(op, ignore -> new AtomicInteger(0))
                .getAndIncrement();
    }
}
