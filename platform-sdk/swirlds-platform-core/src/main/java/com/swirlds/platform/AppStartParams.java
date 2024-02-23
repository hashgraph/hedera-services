/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform;

import java.nio.file.Path;

/**
 * Record that wraps the parameters based on the "app" property in the "config.txt"
 *
 * @param appParameters
 * 		the app parameters
 * @param appJarFilename
 * 		the name of the jar file of the app
 * @param mainClassname
 * 		the main class of the app
 * @param appJarPath
 * 		Path to the jar file
 * @deprecated will be removed once we do not use the format of the config.txt anymore
 */
@Deprecated(forRemoval = true)
public record AppStartParams(String[] appParameters, String appJarFilename, String mainClassname, Path appJarPath) {}
