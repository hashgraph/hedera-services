/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stress;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.validation.annotation.Positive;
import java.time.Duration;

/**
 * Configuration for the Stress Testing Tool
 *
 * @param bytesPerTrans bytes in each transaction
 * @param transPerSecToCreate number of transactions to create per second
 * @param transPoolSize the size of the transaction pool
 * @param handleTime simulated handle time for each consensus transaction
 * @param preHandleTime simulated handle time for each consensus transaction
 */
@ConfigData("stressTestingTool")
public record StressTestingToolConfig(
        @ConfigProperty(defaultValue = "100") @Positive int bytesPerTrans,
        @ConfigProperty(defaultValue = "500") @Positive int transPerSecToCreate,
        @ConfigProperty(defaultValue = "1024") @Positive int transPoolSize,
        @ConfigProperty(defaultValue = "0s") Duration handleTime,
        @ConfigProperty(defaultValue = "0s") Duration preHandleTime) {}
