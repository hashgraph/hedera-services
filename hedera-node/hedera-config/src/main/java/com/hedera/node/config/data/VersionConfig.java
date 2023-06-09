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

package com.hedera.node.config.data;

import com.hedera.hapi.node.base.SemanticVersion;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;

/**
 * The version information is provided as part of the build process and stored in a resource file within the JAR files
 * of the application itself. Previously, these were stored with a slightly different name.
 *
 * @param servicesVersion The version of the services code itself
 * @param hapiVersion The version of the supported HAPI
 */
@ConfigData("version")
public record VersionConfig(
        @ConfigProperty(value = "services", defaultValue = "0.0.0") SemanticVersion servicesVersion,
        @ConfigProperty(value = "hapi", defaultValue = "0.0.0") SemanticVersion hapiVersion) {}
