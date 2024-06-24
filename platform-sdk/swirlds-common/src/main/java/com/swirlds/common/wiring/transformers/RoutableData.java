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

package com.swirlds.common.wiring.transformers;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Data that can be routed to a specific address.
 *
 * @param address       the address to route to (will be an enum value from ROUTER_TYPE)
 * @param data          the data
 * @param <ROUTER_TYPE> the type of the enum that defines the addresses
 */
public record RoutableData<ROUTER_TYPE extends Enum<ROUTER_TYPE>>(@NonNull ROUTER_TYPE address, @NonNull Object data) {}
