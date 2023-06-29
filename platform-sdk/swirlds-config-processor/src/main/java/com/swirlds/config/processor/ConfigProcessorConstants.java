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

package com.swirlds.config.processor;

public class ConfigProcessorConstants {

    public static final String JAVA_FILE_EXTENSION = ".java";
    public static final String JAVADOC_PARAM = "@param";
    public static final String VALUE_FIELD_NAME = "value";
    public static final String DEFAULT_VALUE_FIELD_NAME = "defaultValue";
    public static final String JAVA_LANG_STRING = "java.lang.String";
    public static final String CONSTANTS_CLASS_SUFFIX = "_";
    public static final String CONFIG_DATA_ANNOTATION = "com.swirlds.config.api.ConfigData";

    private ConfigProcessorConstants() {}
}
