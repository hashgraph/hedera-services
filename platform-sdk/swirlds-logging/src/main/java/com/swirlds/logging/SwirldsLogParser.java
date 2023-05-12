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

package com.swirlds.logging;

/**
 * An object that knows how to parse a swirlds log.
 */
public interface SwirldsLogParser<T> {

    /**
     * Parse a line from the log.
     *
     * @param line
     * 		the line to parse
     * @return a log entry if one was found. If the line is invalid then return null.
     */
    T parse(String line);
}
