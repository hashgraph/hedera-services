/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.config;

import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * Basic configuration data record. This record contains all general config properties that can not be defined for a
 * specific subsystem. The record is based on the definition of config data objects as described in {@link ConfigData}.
 *
 * <p>
 * Do not add new settings to this record unless you have a very good reason. New settings should go
 * into config records with a prefix defined by a {@link ConfigData} tag. Adding
 * settings to this record pollutes the top level namespace.
 *
 * @param showInternalStats
 * 		show the user all statistics, including those with category "internal"?
 * @param verboseStatistics
 * 		show expand statistics values, include mean, min, max, stdDev
 */
@ConfigData
public record BasicCommonConfig(
        @ConfigProperty(defaultValue = "true") boolean showInternalStats,
        @ConfigProperty(defaultValue = "false") boolean verboseStatistics) {}
