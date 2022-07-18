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
package com.hedera.services.store.contracts.precompile;

import com.hedera.services.store.contracts.precompile.codec.DecodingFacade;
import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

/**
 * All constants shared by {@link EncodingFacade} and {@link DecodingFacade}, in one place for easy
 * review.
 */
public final class ParsingConstants {
    private ParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // data types
    public static final String ADDRESS = "(address)";
    public static final String ARRAY_BRACKETS = "[]";
    public static final String BOOL = "(bool)";
    public static final String BYTES32 = "(bytes32)";
    public static final String BYTES32_PAIR_RAW_TYPE = "(bytes32,bytes32)";
    public static final String INT = "(int)";
    public static final String STRING = "(string)";
    public static final String UINT8 = "(uint8)";
    public static final String UINT256 = "(uint256)";

    // struct types
    public static final String EXPIRY = "(uint32,address,uint32)";
    public static final String FIXED_FEE = "(uint32,address,bool,bool,address)";
    public static final String FRACTIONAL_FEE = "(uint32,uint32,uint32,uint32,bool,address)";
    public static final String KEY_VALUE = "(bool,address,bytes,bytes,address)";
    public static final String ROYALTY_FEE = "(uint32,uint32,uint32,address,bool,address)";
    public static final String TOKEN_KEY = "(uint256," + KEY_VALUE + ")";
}
