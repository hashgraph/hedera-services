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

import org.apache.tuweni.bytes.Bytes;

/** All ABI constants used by {@link Precompile} implementations, in one place for easy review. */
public final class AbiConstants {
    private AbiConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // **** HIP-206 function selectors ****
    // cryptoTransfer(TokenTransferList[] memory tokenTransfers)
    public static final int ABI_ID_CRYPTO_TRANSFER = 0x189a554c;
    // transferTokens(address token, address[] memory accountId, int64[] memory amount)
    public static final int ABI_ID_TRANSFER_TOKENS = 0x82bba493;
    // transferToken(address token, address sender, address recipient, int64 amount)
    public static final int ABI_ID_TRANSFER_TOKEN = 0xeca36917;
    // transferNFTs(address token, address[] memory sender, address[] memory receiver, int64[]
    // memory
    // serialNumber)
    public static final int ABI_ID_TRANSFER_NFTS = 0x2c4ba191;
    // transferNFT(address token,  address sender, address recipient, int64 serialNum)
    public static final int ABI_ID_TRANSFER_NFT = 0x5cfc9011;
    // mintToken(address token, uint64 amount, bytes[] memory metadata)
    public static final int ABI_ID_MINT_TOKEN = 0x278e0b88;
    // burnToken(address token, uint64 amount, int64[] memory serialNumbers)
    public static final int ABI_ID_BURN_TOKEN = 0xacb9cff9;
    // deleteToken(address token)
    public static final int ABI_ID_DELETE_TOKEN = 0xf069f712;
    // associateTokens(address account, address[] memory tokens)
    public static final int ABI_ID_ASSOCIATE_TOKENS = 0x2e63879b;
    // associateToken(address account, address token)
    public static final int ABI_ID_ASSOCIATE_TOKEN = 0x49146bde;
    // dissociateTokens(address account, address[] memory tokens)
    public static final int ABI_ID_DISSOCIATE_TOKENS = 0x78b63918;
    // dissociateToken(address account, address token)
    public static final int ABI_ID_DISSOCIATE_TOKEN = 0x099794e8;
    // pauseToken(address token)
    public static final int ABI_ID_PAUSE_TOKEN = 0x7c41ad2c;
    // unpauseToken(address token)
    public static final int ABI_ID_UNPAUSE_TOKEN = 0x3b3bff0f;
    // allowance(address token, address owner, address spender)
    public static final int ABI_ID_ALLOWANCE = 0x927da105;
    // approve(address token, address spender, uint256 amount)
    public static final int ABI_ID_APPROVE = 0xe1f21c67;
    // approveNFT(address token, address to, uint256 tokenId)
    public static final int ABI_ID_APPROVE_NFT = 0x7336aaf0;
    // setApprovalForAll(address token, address operator, bool approved)
    public static final int ABI_ID_SET_APPROVAL_FOR_ALL = 0x367605ca;
    // getApproved(address token, uint256 tokenId)
    public static final int ABI_ID_GET_APPROVED = 0x098f2366;
    // isApprovedForAll(address token, address owner, address operator)
    public static final int ABI_ID_IS_APPROVED_FOR_ALL = 0xf49f40db;
    // transferFrom(address token, address from, address to, uint256 amount)
    public static final int ABI_ID_TRANSFER_FROM = 0x15dacbea;
    // transferFromNFT(address token, address from, address to, uint256 serialNumber)
    public static final int ABI_ID_TRANSFER_FROM_NFT = 0x9b23d3d9;

    // **** HIP-218 + HIP-376 function selectors and event signatures ****
    // redirectForToken(address token, bytes memory data)
    public static final int ABI_ID_REDIRECT_FOR_TOKEN = 0x618dc65e;
    // name()
    public static final int ABI_ID_ERC_NAME = 0x06fdde03;
    // symbol()
    public static final int ABI_ID_ERC_SYMBOL = 0x95d89b41;
    // decimals()
    public static final int ABI_ID_ERC_DECIMALS = 0x313ce567;
    // totalSupply()
    public static final int ABI_ID_ERC_TOTAL_SUPPLY_TOKEN = 0x18160ddd;
    // balanceOf(address account)
    public static final int ABI_ID_ERC_BALANCE_OF_TOKEN = 0x70a08231;
    // transfer(address recipient, uint256 amount)
    public static final int ABI_ID_ERC_TRANSFER = 0xa9059cbb;
    // transferFrom(address sender, address recipient, uint256 amount)
    // transferFrom(address from, address to, uint256 tokenId)
    public static final int ABI_ID_ERC_TRANSFER_FROM = 0x23b872dd;
    // allowance(address owner, address spender)
    public static final int ABI_ID_ERC_ALLOWANCE = 0xdd62ed3e;
    // approve(address spender, uint256 amount)
    // approve(address to, uint256 tokenId)
    public static final int ABI_ID_ERC_APPROVE = 0x95ea7b3;
    // setApprovalForAll(address operator, bool approved)
    public static final int ABI_ID_ERC_SET_APPROVAL_FOR_ALL = 0xa22cb465;
    // getApproved(uint256 tokenId)
    public static final int ABI_ID_ERC_GET_APPROVED = 0x081812fc;
    // isApprovedForAll(address owner, address operator)
    public static final int ABI_ID_ERC_IS_APPROVED_FOR_ALL = 0xe985e9c5;
    // ownerOf(uint256 tokenId)
    public static final int ABI_ID_ERC_OWNER_OF_NFT = 0x6352211e;
    // tokenURI(uint256 tokenId)
    public static final int ABI_ID_ERC_TOKEN_URI_NFT = 0xc87b56dd;
    // wipeTokenAccount(address, address, uint32)
    public static final int ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE = 0x9790686d;
    // wipeTokenAccountNFT(address, address, int64[])
    public static final int ABI_WIPE_TOKEN_ACCOUNT_NFT = 0xf7f38e26;
    // isFrozen(address token, address account)
    public static final int ABI_ID_IS_FROZEN = 0x46de0fb1;
    // freezeToken(address token, address account)
    public static final int ABI_ID_FREEZE = 0x5b8f8584;
    // unfreezeToken(address token, address account)
    public static final int ABI_ID_UNFREEZE = 0x52f91387;
    // updateTokenInfo(address token, HederaToken tokenInfo)
    public static final int ABI_ID_UPDATE_TOKEN_INFO = 0x2cccc36f;
    // updateTokenKeys(address token, TokenKey [])
    public static final int ABI_ID_UPDATE_TOKEN_INFO_V2 = 0x18370d34;
    // updateTokenKeys(address token, TokenKey [])
    public static final int ABI_ID_UPDATE_TOKEN_KEYS = 0x6fc3cbaf;
    // getTokenKey(address token, uint tokenType)
    public static final int ABI_ID_GET_TOKEN_KEY = 0x3c4dd32e;
    // Transfer(address indexed from, address indexed to, uint256 indexed tokenId)
    // Transfer(address indexed from, address indexed to, uint256 value)
    public static final Bytes TRANSFER_EVENT =
            Bytes.fromHexString("ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    // Approval(address indexed owner, address indexed spender, uint256 value)
    // Approval(address indexed owner, address indexed approved, uint256 indexed tokenId)
    public static final Bytes APPROVAL_EVENT =
            Bytes.fromHexString("8c5be1e5ebec7d5bd14f71427d1e84f3dd0314c0f7b2291e5b200ac8c7c3b925");
    // ApprovalForAll(address indexed owner, address indexed operator, bool approved)
    public static final Bytes APPROVAL_FOR_ALL_EVENT =
            Bytes.fromHexString("17307eab39ab6107e8899845ad3d59bd9653f200f220920489ca2b5937696c31");

    // **** HIP-358 function selectors ****
    // createFungibleToken(HederaToken memory token, uint initialTotalSupply, uint decimals)
    public static final int ABI_ID_CREATE_FUNGIBLE_TOKEN = 0x7812a04b;
    // createFungibleToken(HederaToken memory token, uint64 initialTotalSupply, uint32 decimals)
    public static final int ABI_ID_CREATE_FUNGIBLE_TOKEN_V2 = 0xc23baeb6;
    // createFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  uint initialTotalSupply,
    //  uint decimals,
    //  FixedFee[] memory fixedFees,
    //  FractionalFee[] memory fractionalFees)
    public static final int ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES = 0x4c381ae7;
    // createFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  uint64 initialTotalSupply,
    //  uint32 decimals,
    //  FixedFee[] memory fixedFees,
    //  FractionalFee[] memory fractionalFees)
    public static final int ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES_V2 = 0xb937581a;
    // createNonFungibleToken(HederaToken memory token)
    public static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN = 0x9dc711e0;
    // createNonFungibleToken(HederaToken memory token)
    // HederaToken field maxSupply updated to int64
    public static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_V2 = 0x9c89bb35;
    // createNonFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  FixedFee[] memory fixedFees,
    //  RoyaltyFee[] memory royaltyFees)
    public static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES = 0x5bc7c0e6;
    // createNonFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  FixedFee[] memory fixedFees,
    //  RoyaltyFee[] memory royaltyFees)
    //  HederaToken field maxSupply updated to int64
    public static final int ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES_V2 = 0x45733969;

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
    // 	grantTokenKyc(address  token, address account)
    public static final int ABI_ID_GRANT_TOKEN_KYC = 0x8f8d7f99;
    // revokeTokenKyc(address token, address account)
    public static final int ABI_ID_REVOKE_TOKEN_KYC = 0xaf99c633;
    // getTokenCustomFees(address token)
    public static final int ABI_ID_GET_TOKEN_CUSTOM_FEES = 0xae7611a0;
    // isToken(address token)
    public static final int ABI_ID_IS_TOKEN = 0x19f37361;
    // getTokenType(address token)
    public static final int ABI_ID_GET_TOKEN_TYPE = 0x93272baf;
    // getTokenExpiryInfo(address token)
    public static final int ABI_ID_GET_TOKEN_EXPIRY_INFO = 0xd614cdb8;
    // updateTokenExpiryInfo(address token, Expiry expiryInfoStruct)
    public static final int ABI_ID_UPDATE_TOKEN_EXPIRY_INFO = 0x593d6e82;
}
