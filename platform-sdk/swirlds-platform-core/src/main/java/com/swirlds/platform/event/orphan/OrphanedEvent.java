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

package com.swirlds.platform.event.orphan;

import com.swirlds.common.event.EventDescriptorWrapper;
import com.swirlds.common.event.PlatformEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * An event that is missing one or more parents.
 *
 * @param orphan         the event that is missing one or more parents
 * @param missingParents the list of missing parents (ancient parents are not included)
 */
record OrphanedEvent(@NonNull PlatformEvent orphan, @NonNull List<EventDescriptorWrapper> missingParents) {}
