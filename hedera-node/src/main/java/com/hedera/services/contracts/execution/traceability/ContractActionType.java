/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.execution.traceability;

public enum ContractActionType {

    /** default non-value. */
    NO_ACTION,

    /**
     * Most CALL, CALLCODE, DELEGATECALL, and STATICCALL, and first action of
     * ContractCall/ContractCallLocal to deployed contracts. This does not include calls to system
     * or precompiled contracts.
     */
    CALL,

    /** CREATE, CREATE2, and first action of ContractCreate. */
    CREATE,

    /** like Call, but to precompiled contracts (0x1 to 0x9 as of Berlin) */
    PRECOMPILE,

    /** Call, but to system contract like HTS or ERC20 facades over Token accounts */
    SYSTEM
}
