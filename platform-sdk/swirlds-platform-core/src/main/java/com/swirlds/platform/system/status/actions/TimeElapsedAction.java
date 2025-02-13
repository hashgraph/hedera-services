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

package com.swirlds.platform.system.status.actions;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * An action that indicates an amount of wall clock time has elapsed.
 * <p>
 * Triggered periodically when other actions aren't being processed.
 *
 * @param instant the instant when this action was triggered
 */
public record TimeElapsedAction(@NonNull Instant instant) implements PlatformStatusAction {}
