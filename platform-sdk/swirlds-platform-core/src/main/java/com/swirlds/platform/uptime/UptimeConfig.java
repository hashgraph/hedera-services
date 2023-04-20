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

package com.swirlds.platform.uptime;

import com.swirlds.config.api.ConfigProperty;
import java.time.Duration;

/**
 * Configuration for the uptime detector.
 *
 * @param degradationThreshold if none of a node's events reach consensus in this amount of time then we consider that
 *                             node to be degraded.
 */
public record UptimeConfig(@ConfigProperty(defaultValue = "10s") Duration degradationThreshold) {}
