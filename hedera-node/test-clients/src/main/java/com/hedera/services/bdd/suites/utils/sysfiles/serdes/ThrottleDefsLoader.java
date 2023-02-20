/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import com.hedera.node.app.hapi.utils.sysfiles.serdes.ThrottlesJsonToProtoSerde;
import com.hederahashgraph.api.proto.java.ThrottleDefinitions;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public class ThrottleDefsLoader {
    public static ThrottleDefinitions protoDefsFromResource(String testResource) {
        try (InputStream in = ThrottlesJsonToProtoSerde.class.getClassLoader().getResourceAsStream(testResource)) {
            return ThrottlesJsonToProtoSerde.loadProtoDefs(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
