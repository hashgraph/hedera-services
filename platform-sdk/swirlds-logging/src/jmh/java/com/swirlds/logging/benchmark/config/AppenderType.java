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

package com.swirlds.logging.benchmark.config;

public enum AppenderType {
    CONSOLE_ONLY,
    ROLLING_FILE_ONLY,
    SINGLE_FILE_ONLY,
    CONSOLE_AND_ROLLING_FILE,
    CONSOLE_AND_SINGLE_FILE;

    public static AppenderType fromString(String appenderType, String fileRollingMode) {
        switch (appenderType) {
            case Constants.CONSOLE_TYPE:
                return CONSOLE_ONLY;
            case Constants.FILE_TYPE:
                return FileRollingMode.fromString(fileRollingMode) == FileRollingMode.ROLLING
                        ? ROLLING_FILE_ONLY
                        : SINGLE_FILE_ONLY;
            case Constants.CONSOLE_AND_FILE_TYPE:
                return FileRollingMode.fromString(fileRollingMode) == FileRollingMode.ROLLING
                        ? CONSOLE_AND_ROLLING_FILE
                        : CONSOLE_AND_SINGLE_FILE;
            default:
                throw new IllegalArgumentException("Unknown appender type: " + appenderType);
        }
    }

    public FileRollingMode getFileRollingMode() {
        switch (this) {
            case ROLLING_FILE_ONLY:
            case CONSOLE_AND_ROLLING_FILE:
                return FileRollingMode.ROLLING;
            default:
                return FileRollingMode.NO_ROLLING;
        }
    }
}
