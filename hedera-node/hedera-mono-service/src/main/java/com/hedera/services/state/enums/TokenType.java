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
 * Token Types of {@link com.hedera.services.state.merkle.MerkleToken} Apart from fungible and
 * non-fungible, Tokens can have either a common or unique representation. This distinction might
 * seem subtle, but it is important when considering how tokens can be traced and if they can have
 * isolated and unique properties.
 */
public enum TokenType {
    /**
     * Interchangeable value with one another, where any quantity of them has the same value as
     * another equal quantity if they are in the same class. Share a single set of properties, not
     * distinct from one another. Simply represented as a balance or quantity to a given Hedera
     * account.
     */
    FUNGIBLE_COMMON,
    /**
     * Unique, not interchangeable with other tokens of the same type as they typically have
     * different values. Individually traced and can carry unique properties (e.g. serial number).
     */
    NON_FUNGIBLE_UNIQUE
}
