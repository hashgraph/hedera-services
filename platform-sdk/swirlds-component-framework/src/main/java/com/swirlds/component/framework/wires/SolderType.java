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

package com.swirlds.component.framework.wires;

import com.swirlds.component.framework.wires.input.InputWire;

/**
 * The type of solder connection between an output wire and an input wire.
 */
public enum SolderType {
    /**
     * When data is passed to the input wire, call {@link InputWire#put(Object)}. May block if the input wire has
     * backpressure enabled and the input wire is full.
     */
    PUT,
    /**
     * When data is passed to the input wire, call {@link InputWire#inject(Object)}. Ignores back pressure.
     */
    INJECT,
    /**
     * When data is passed to the input wire, call {@link InputWire#offer(Object)}. If the input wire has backpressure
     * enabled and the input wire is full, then the data will be dropped.
     */
    OFFER
}
