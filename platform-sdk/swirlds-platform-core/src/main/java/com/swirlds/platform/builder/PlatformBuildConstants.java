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

package com.swirlds.platform.builder;

/**
 * Constants used in the platform build process.
 */
public final class PlatformBuildConstants {

    /**
     * The name of the log4j configuration file.
     */
    public static final String LOG4J_FILE_NAME = "log4j2.xml";

    /**
     * The name of the default configuration file (i.e. where the initial address book can be specified).
     */
    public static final String DEFAULT_CONFIG_FILE_NAME = "config.txt";

    /**
     * The name of the default settings file (i.e. where configuration comes from... we know this is confusing, API is a
     * work in progress).
     */
    public static final String DEFAULT_SETTINGS_FILE_NAME = "settings.txt";

    private PlatformBuildConstants() {}
}
