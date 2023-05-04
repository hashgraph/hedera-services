/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.spi.config.data;

import com.hedera.node.app.spi.config.types.Profile;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

@ConfigData("netty")
public record NettyConfig(@ConfigProperty Profile mode,
                          @ConfigProperty("prod.flowControlWindow") int prodFlowControlWindow,
                          @ConfigProperty("prod.maxConcurrentCalls") int prodMaxConcurrentCalls,
                          @ConfigProperty("prod.maxConnectionAge") long prodMaxConnectionAge,
                          @ConfigProperty("prod.maxConnectionAgeGrace") long prodMaxConnectionAgeGrace,
                          @ConfigProperty("prod.maxConnectionIdle") long prodMaxConnectionIdle,
                          @ConfigProperty("prod.keepAliveTime") long prodKeepAliveTime,
                          @ConfigProperty("prod.keepAliveTimeout") long prodKeepAliveTimeout,
                          @ConfigProperty int startRetries,
                          @ConfigProperty long startRetryIntervalMs,
                          @ConfigProperty("tlsCrt.path") String tlsCrtPath,
                          @ConfigProperty("tlsKey.path") String tlsKeyPath) {

}
