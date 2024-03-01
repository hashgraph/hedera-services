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

package com.swirlds.logging.benchmark;

public enum LoggingHandlingType {
    CONSOLE,
    FILE,
    CONSOLE_AND_FILE;

    public static final String CONSOLE_TYPE = "CONSOLE";

    public static final String FILE_TYPE = "FILE";

    public static final String CONSOLE_AND_FILE_TYPE = "CONSOLE_AND_FILE";
}
