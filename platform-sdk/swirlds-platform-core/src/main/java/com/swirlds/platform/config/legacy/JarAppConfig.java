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

package com.swirlds.platform.config.legacy;

/**
 * This record defines the set of parameters that can be defined for the {@code app} property in the legacy config.txt
 * file.
 *
 * @param jarName
 * 		the jarname param
 * @param params
 * 		all params (including "app" as the first param and thejarname param as second param)
 * @deprecated will be removed once we have removed the "legacy" {@code config.txt} file.
 */
@Deprecated(forRemoval = true)
public record JarAppConfig(String jarName, String[] params) {}
