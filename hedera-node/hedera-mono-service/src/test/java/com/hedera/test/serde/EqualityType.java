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

package com.hedera.test.serde;

/**
 * Used by the base {@link SelfSerializableDataTest} to signal to expected object providers what type of
 * equality test will be used.
 *
 * <p>We added this so that when {@code OBJECT_EQUALITY} is being used, we can insert an extra step of
 * converting the expected object to-and-from the corresponding PBJ type <I>before</I> returning it.
 * (This is an efficient way to confirm our bidirectional converters are not changing object semantics.)
 */
public enum EqualityType {
    /**
     * A test is using object equality to compare expected and actual objects.
     */
    OBJECT_EQUALITY,
    /**
     * A test is using serialized bytes equality to compare expected and actual objects.
     */
    SERIALIZED_EQUALITY
}
