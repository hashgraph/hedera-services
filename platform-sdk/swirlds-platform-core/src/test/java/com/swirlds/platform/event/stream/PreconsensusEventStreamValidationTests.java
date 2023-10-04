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

package com.swirlds.platform.event.stream;

import org.junit.jupiter.api.DisplayName;

@DisplayName("PreconsensusEventStreamValidation Test")
class PreconsensusEventStreamValidationTests {

    /* TODO
     * <li>that there exists at least one PCES file</li>
     * <li>that the generations stored by the PCES "cover" all states on disk</li>
     * <li>that the number of discontinuities in the stream do not exceed a specified maximum</li>
     * <li>files do not contain events with illegal generations</li>
     * <li>events are in topological order</li>
     * <li>events only show up once in a stream (violations are permitted when there are discontinuities)</li>
     * <li>checks performed by {@link PreconsensusEventFileReader#fileSanityChecks(
     *boolean, long, long, long, long, Instant, PreconsensusEventFile) fileSanityChecks()}</li>
     */
}
