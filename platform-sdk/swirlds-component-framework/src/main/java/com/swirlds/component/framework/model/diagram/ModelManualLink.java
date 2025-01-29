/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.model.diagram;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Describes a manual link between two components. Useful for adding information to the diagram that is not captured by
 * the wiring framework
 *
 * @param source the source scheduler
 * @param label  the label on the edge
 * @param target the target scheduler
 */
public record ModelManualLink(@NonNull String source, @NonNull String label, @NonNull String target) {}
