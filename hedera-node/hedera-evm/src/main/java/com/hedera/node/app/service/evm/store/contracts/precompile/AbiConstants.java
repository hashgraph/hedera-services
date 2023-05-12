/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.evm.store.contracts.precompile;

public class AbiConstants {

    private AbiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // **** HIP-218 + HIP-376 function selectors and event signatures ****
    // redirectForToken(address token, bytes memory data)
    public static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;
    // name()
    public static final int ABI_ID_ERC_NAME = 0x06fdde03;
    // symbol()
    public static final int ABI_ID_ERC_SYMBOL = 0x95d89b41;
    // allowance(address owner, address spender)
    public static final int ABI_ID_ERC_ALLOWANCE = 0xdd62ed3e;
    // getApproved(uint256 tokenId)
    public static final int ABI_ID_ERC_GET_APPROVED = 0x081812fc;
    // isApprovedForAll(address owner, address operator)
    public static final int ABI_ID_ERC_IS_APPROVED_FOR_ALL = 0xe985e9c5;
    // decimals()
    public static final int ABI_ID_ERC_DECIMALS = 0x313ce567;
    // totalSupply()
    public static final int ABI_ID_ERC_TOTAL_SUPPLY_TOKEN = 0x18160ddd;
    // balanceOf(address account)
    public static final int ABI_ID_ERC_BALANCE_OF_TOKEN = 0x70a08231;
    // ownerOf(uint256 tokenId)
    public static final int ABI_ID_ERC_OWNER_OF_NFT = 0x6352211e;
    // tokenURI(uint256 tokenId)
    public static final int ABI_ID_ERC_TOKEN_URI_NFT = 0xc87b56dd;

    // isFrozen(address token, address account)
    public static final int ABI_ID_IS_FROZEN = 0x46de0fb1;
    // getTokenKey(address token, uint tokenType)
    public static final int ABI_ID_GET_TOKEN_KEY = 0x3c4dd32e;

    // **** HIP-514 function selectors ****
    // getFungibleTokenInfo(address token)
    public static final int ABI_ID_GET_FUNGIBLE_TOKEN_INFO = 0x3f28a19b;
    // getTokenInfo(address token)
    public static final int ABI_ID_GET_TOKEN_INFO = 0x1f69565f;
    // getNonFungibleTokenInfo(address token, int64 serialNumber)
    public static final int ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO = 0x287e1da8;
    // getTokenDefaultFreezeStatus(address token)
    public static final int ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS = 0xa7daa18d;
    // getTokenDefaultKycStatus(address token)
    public static final int ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS = 0x335e04c1;
    // isKyc(address token, address account)
    public static final int ABI_ID_IS_KYC = 0xf2c31ff4;

    // getTokenCustomFees(address token)
    public static final int ABI_ID_GET_TOKEN_CUSTOM_FEES = 0xae7611a0;
    // isToken(address token)
    public static final int ABI_ID_IS_TOKEN = 0x19f37361;
    // getTokenType(address token)
    public static final int ABI_ID_GET_TOKEN_TYPE = 0x93272baf;
    // getTokenExpiryInfo(address token)
    public static final int ABI_ID_GET_TOKEN_EXPIRY_INFO = 0xd614cdb8;
}
