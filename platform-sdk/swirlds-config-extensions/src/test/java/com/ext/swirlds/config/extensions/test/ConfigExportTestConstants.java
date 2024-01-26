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

package com.ext.swirlds.config.extensions.test;

import com.swirlds.config.api.ConfigData;

/**
 * Helper for {@code ConfigExportTest}
 */
public class ConfigExportTestConstants {

    // Following classes are inner to this one so that they are outside the com.swirlds package and avoids being picked
    // up
    // by the framework. They are specifically added in ConfigExportTest
    @ConfigData
    public record ConfigExportTestRecord(String property) {}

    @ConfigData("prefix")
    public record PrefixedConfigExportTestRecord(String property) {}
}
