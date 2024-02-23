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

package com.swirlds.fchashmap;

/**
 * A value from an {@link FCHashMap} that is safe to modify.
 *
 * @param value
 * 		the value from the FCHashMap, or null if no value exists
 * @param original
 * 		the original value that was copied. Equal to value if there was no copying performed
 * @param <V>
 * 		the type of the value
 */
public record ModifiableValue<V>(V value, V original) {}
