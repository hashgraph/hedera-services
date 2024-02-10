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

package com.hedera.node.app.service.mono.store.contracts.precompile.impl;

import com.hedera.node.app.hapi.utils.contracts.ParsingConstants;
import com.hedera.node.app.service.mono.store.contracts.precompile.codec.DecodingFacade;

/**
 * Solidity types used by ABI methods defined in `SystemContractAbis`
 * <p>
 * Must be broken out into a separate class to avoid forward references if they're put where
 * they're more appropriate: Right into the enum declaration `SystemContractAbis`.
 */
public final class SystemContractTypes {

    public static final String ACCOUNT_AMOUNT_V1 = "(address,int64)";
    public static final String ACCOUNT_AMOUNT_V2 = "(address,int64,bool)";

    public static final String EXPIRY_V1 = ParsingConstants.EXPIRY;
    public static final String EXPIRY_V2 = ParsingConstants.EXPIRY_V2;

    public static final String FIXED_FEE_V1 = ParsingConstants.FIXED_FEE;
    public static final String FIXED_FEE_V2 = ParsingConstants.FIXED_FEE_V2;

    public static final String FRACTIONAL_FEE_V1 = ParsingConstants.FRACTIONAL_FEE;
    public static final String FRACTIONAL_FEE_V2 = ParsingConstants.FRACTIONAL_FEE_V2;

    public static final String HEDERA_TOKEN_STRUCT_V1 = DecodingFacade.HEDERA_TOKEN_STRUCT;
    public static final String HEDERA_TOKEN_STRUCT_V2 = DecodingFacade.HEDERA_TOKEN_STRUCT_V2;
    public static final String HEDERA_TOKEN_STRUCT_V3 = DecodingFacade.HEDERA_TOKEN_STRUCT_V3;

    public static final String NFT_TRANSFER_V1 = "(address,address,int64)";
    public static final String NFT_TRANSFER_V2 = "(address,address,int64,bool)";

    public static final String ROYALTY_FEE_V1 = ParsingConstants.ROYALTY_FEE;
    public static final String ROYALTY_FEE_V2 = ParsingConstants.ROYALTY_FEE_V2;

    public static final String TOKEN_TRANSFER_LIST_V1 =
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_V1, NFT_TRANSFER_V1);
    public static final String TOKEN_TRANSFER_LIST_V2 =
            "(address,%s[],%s[])".formatted(ACCOUNT_AMOUNT_V2, NFT_TRANSFER_V2);

    public static final String TRANSFER_LIST_V1 = "(%s[])".formatted(ACCOUNT_AMOUNT_V2);

    private SystemContractTypes() {}
}
