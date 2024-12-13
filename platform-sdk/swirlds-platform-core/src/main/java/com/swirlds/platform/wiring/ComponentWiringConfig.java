/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.wiring;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Configuration related to how platform components are wired together.
 *
 * @param inlinePces if true, pre-consensus events will be written to disk before being gossipped, this will ensure that
 *                   a node can never lose an event that it has created due to a crash
 */
@ConfigData("platformWiring")
public record ComponentWiringConfig(@ConfigProperty(defaultValue = "true") boolean inlinePces) {}
