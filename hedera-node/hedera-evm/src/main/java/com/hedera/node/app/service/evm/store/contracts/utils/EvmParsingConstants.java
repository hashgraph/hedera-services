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
package com.hedera.node.app.service.evm.store.contracts.utils;

public class EvmParsingConstants {

    private EvmParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static final String INT = "(int)";
    public static final String BYTES32 = "(bytes32)";
    public static final String UINT256 = "(uint256)";
    public static final String BOOL = "(bool)";
    public static final String STRING = "(string)";
    public static final String ADDRESS_UINT256_RAW_TYPE = "(bytes32,uint256)";
    public static final String INT_BOOL_PAIR = "(int,bool)";
    public static final String ADDRESS_TRIO_RAW_TYPE = "(bytes32,bytes32,bytes32)";

    public static final String ADDRESS_PAIR_RAW_TYPE = "(bytes32,bytes32)";
}
