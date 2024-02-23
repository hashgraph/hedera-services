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

package com.swirlds.platform.recovery.emergencyfile;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.swirlds.common.jackson.InstantDeserializer;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Data about the bootstrap state loaded during event recovery (the starting state)
 *
 * @param timestamp
 * 		the consensus timestamp of the bootstrap state
 */
public record Bootstrap(
        @NonNull @JsonSerialize(using = ToStringSerializer.class) @JsonDeserialize(using = InstantDeserializer.class)
                Instant timestamp) {}
