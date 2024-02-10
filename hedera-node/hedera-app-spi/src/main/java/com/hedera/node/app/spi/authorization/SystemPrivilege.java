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

package com.hedera.node.app.spi.authorization;

/**
 * The possible results of a system privilege check.
 */
public enum SystemPrivilege {
    /** The operation does not require any system privileges. */
    UNNECESSARY,

    /** The operation requires system privileges that its payer does not have. */
    UNAUTHORIZED,

    /** The operation cannot be performed, no matter the privileges of its payer. */
    IMPERMISSIBLE,

    /** The operation requires system privileges, and its payer has those privileges. */
    AUTHORIZED;
}
