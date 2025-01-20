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

package com.swirlds.component.framework.model.internal.analysis;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Uniquely describes an input wire within a wiring model.
 *
 * <p>
 * This object exists so that standard input wires don't have to implement equals and hash code.
 *
 * @param taskSchedulerName the name of the task scheduler the input wire is bound to
 * @param name              the name of the input wire
 */
public record InputWireDescriptor(@NonNull String taskSchedulerName, @NonNull String name) {}
