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

package com.swirlds.config.processor;

/**
 * Constants used by the config processor.
 */
public final class ConfigProcessorConstants {

    /**
     * The extension for java files.
     */
    public static final String JAVA_FILE_EXTENSION = ".java";

    /**
     * Suffix for the generated classes
     */
    public static final String CONSTANTS_CLASS_SUFFIX = "_";

    /**
     * Class name of the ConfigData annotation.
     */
    public static final String CONFIG_DATA_ANNOTATION = "com.swirlds.config.api.ConfigData";

    /**
     * private constructor for utility class.
     */
    private ConfigProcessorConstants() {}
}
