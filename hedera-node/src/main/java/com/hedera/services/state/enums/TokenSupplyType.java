/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.state.enums;

/**
 * Token Supply Types of {@link com.hedera.services.state.merkle.MerkleToken} Indicates how many
 * tokens can have during its lifetime.
 */
public enum TokenSupplyType {
    // Indicates that tokens of that type have an upper bound of Long.MAX_VALUE.
    INFINITE,
    // Indicates that tokens of that type have an upper bound of maxSupply, provided on token
    // creation.
    FINITE
}
