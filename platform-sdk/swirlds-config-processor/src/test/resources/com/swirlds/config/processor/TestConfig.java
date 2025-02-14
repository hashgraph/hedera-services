// SPDX-License-Identifier: Apache-2.0
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

package com.swirlds.config.processor;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Config that control the SignedStateManager and SignedStateFileManager behaviors.
 *
 * @param saveStatePeriod The frequency of writes of a state to disk every this
 *                       many seconds (0 to never write).
 */
@ConfigData("test")
public record TestConfig(@ConfigProperty(defaultValue = "900") int saveStatePeriod) {
}
