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
package com.hedera.services.bdd.suites.utils.sysfiles.serdes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.bdd.suites.utils.sysfiles.ExchangeRatesPojo;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import java.io.IOException;

public class XRatesJsonToGrpcBytes implements SysFileSerde<String> {
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String fromRawFile(byte[] bytes) {
        try {
            var pojoRates = ExchangeRatesPojo.fromProto(ExchangeRateSet.parseFrom(bytes));
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(pojoRates);
        } catch (InvalidProtocolBufferException | JsonProcessingException e) {
            throw new IllegalArgumentException("Not an exchange rates set!", e);
        }
    }

    @Override
    public byte[] toRawFile(String styledFile) {
        try {
            var pojoRates = mapper.readValue(styledFile, ExchangeRatesPojo.class);
            return pojoRates.toProto().toByteArray();
        } catch (IOException e) {
            throw new IllegalArgumentException("Not an exchange rates set!", e);
        }
    }

    @Override
    public String preferredFileName() {
        return "exchangeRates.json";
    }
}
