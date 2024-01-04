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

package com.swirlds.platform.event.preconsensus;

/**
 * The type of the PCES file. There are currently two types: one bound by generations and one bound by birth rounds.
 * The original type of files are bound by generations. The new type of files are bound by birth rounds. Once
 * migration has been completed to birth round bound files, support for the generation bound files will be removed.
 */
public enum PcesFileType {
    /**
     * A PCES file bound by generations.
     */
    GENERATION_BOUND,
    /**
     * A PCES file bound by birth rounds.
     */
    BIRTH_ROUND_BOUND
}
