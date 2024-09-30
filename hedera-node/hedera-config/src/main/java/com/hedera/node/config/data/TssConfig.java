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

package com.hedera.node.config.data;

import com.hedera.node.config.NetworkProperty;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration for the TSS service.
 * @param maxSharesPerNode The maximum number of shares that can be assigned to a node.
 * @param keyActiveRoster A test-only configuration set to true to key the active roster with TSS key material.
 */
@ConfigData("tss")
public record TssConfig(
        @ConfigProperty(defaultValue = "3") @NetworkProperty long maxSharesPerNode,
        @ConfigProperty(defaultValue = "false") @NetworkProperty boolean keyActiveRoster) {}
