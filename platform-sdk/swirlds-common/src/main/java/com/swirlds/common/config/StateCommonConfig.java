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
import java.nio.file.Path;

/**
 * This class represents the configuration for the state common settings.
 *
 * @param savedStateDirectory           The directory where states are saved. This is relative to the current working
 *                                      directory, unless the provided path begins with "/", in which case it will be
 *                                      interpreted as an absolute path.
 */
@ConfigData("state")
public record StateCommonConfig(@ConfigProperty(defaultValue = "data/saved") Path savedStateDirectory) {}
