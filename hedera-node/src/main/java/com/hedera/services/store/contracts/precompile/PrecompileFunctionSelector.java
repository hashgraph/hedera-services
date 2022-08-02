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

/** All ABI constants used by {@link Precompile} implementations, in one place for easy review. */
public enum PrecompileFunctionSelector {
    // **** HIP-206 function selectors ****
    // cryptoTransfer(TokenTransferList[] memory tokenTransfers)
    ABI_ID_CRYPTO_TRANSFER(0x189a554c),
    // transferTokens(address token, address[] memory accountId, int64[] memory amount)
    ABI_ID_TRANSFER_TOKENS(0x82bba493),
    // transferToken(address token, address sender, address recipient, int64 amount)
    ABI_ID_TRANSFER_TOKEN(0xeca36917),
    // transferNFTs(address token, address[] memory sender, address[] memory receiver, int64[]
    // memory
    // serialNumber)
    ABI_ID_TRANSFER_NFTS(0x2c4ba191),
    // transferNFT(address token,  address sender, address recipient, int64 serialNum)
    ABI_ID_TRANSFER_NFT(0x5cfc9011),
    // mintToken(address token, uint64 amount, bytes[] memory metadata)
    ABI_ID_MINT_TOKEN(0x278e0b88),
    // burnToken(address token, uint64 amount, int64[] memory serialNumbers)
    ABI_ID_BURN_TOKEN(0xacb9cff9),
    // deleteToken(address token)
    ABI_ID_DELETE_TOKEN(0xf069f712),
    // associateTokens(address account, address[] memory tokens)
    ABI_ID_ASSOCIATE_TOKENS(0x2e63879b),
    // associateToken(address account, address token)
    ABI_ID_ASSOCIATE_TOKEN(0x49146bde),
    // dissociateTokens(address account, address[] memory tokens)
    ABI_ID_DISSOCIATE_TOKENS(0x78b63918),
    // dissociateToken(address account, address token)
    ABI_ID_DISSOCIATE_TOKEN(0x099794e8),
    // pauseToken(address token)
    ABI_ID_PAUSE_TOKEN(0x7c41ad2c),
    // unpauseToken(address token)
    ABI_ID_UNPAUSE_TOKEN(0x3b3bff0f),
    // allowance(address token, address owner, address spender)
    ABI_ID_ALLOWANCE(0x927da105),
    // approve(address token, address spender, uint256 amount)
    ABI_ID_APPROVE(0xe1f21c67),
    // approveNFT(address token, address to, uint256 tokenId)
    ABI_ID_APPROVE_NFT(0x7336aaf0),
    // setApprovalForAll(address token, address operator, bool approved)
    ABI_ID_SET_APPROVAL_FOR_ALL(0x367605ca),
    // getApproved(address token, uint256 tokenId)
    ABI_ID_GET_APPROVED(0x098f2366),
    // isApprovedForAll(address token, address owner, address operator)
    ABI_ID_IS_APPROVED_FOR_ALL(0xf49f40db),

    // **** HIP-218 + HIP-376 function selectors and event signatures ****
    // redirectForToken(address token, bytes memory data)
    ABI_ID_REDIRECT_FOR_TOKEN(0x618dc65e),
    // name()
    ABI_ID_ERC_NAME(0x06fdde03),
    // symbol()
    ABI_ID_ERC_SYMBOL(0x95d89b41),
    // decimals()
    ABI_ID_ERC_DECIMALS(0x313ce567),
    // totalSupply()
    ABI_ID_ERC_TOTAL_SUPPLY_TOKEN(0x18160ddd),
    // balanceOf(address account)
    ABI_ID_ERC_BALANCE_OF_TOKEN(0x70a08231),
    // transfer(address recipient, uint256 amount)
    ABI_ID_ERC_TRANSFER(0xa9059cbb),
    // transferFrom(address sender, address recipient, uint256 amount)
    // transferFrom(address from, address to, uint256 tokenId)
    ABI_ID_ERC_TRANSFER_FROM(0x23b872dd),
    // allowance(address owner, address spender)
    ABI_ID_ERC_ALLOWANCE(0xdd62ed3e),
    // approve(address spender, uint256 amount)
    // approve(address to, uint256 tokenId)
    ABI_ID_ERC_APPROVE(0x95ea7b3),
    // setApprovalForAll(address operator, bool approved)
    ABI_ID_ERC_SET_APPROVAL_FOR_ALL(0xa22cb465),
    // getApproved(uint256 tokenId)
    ABI_ID_ERC_GET_APPROVED(0x081812fc),
    // isApprovedForAll(address owner, address operator)
    ABI_ID_ERC_IS_APPROVED_FOR_ALL(0xe985e9c5),
    // ownerOf(uint256 tokenId)
    ABI_ID_ERC_OWNER_OF_NFT(0x6352211e),
    // tokenURI(uint256 tokenId)
    ABI_ID_ERC_TOKEN_URI_NFT(0xc87b56dd),
    // wipeTokenAccount(address, address, uint32)
    ABI_WIPE_TOKEN_ACCOUNT_FUNGIBLE(0x9790686d),
    // wipeTokenAccountNFT(address, address, int64[])
    ABI_WIPE_TOKEN_ACCOUNT_NFT(0xf7f38e26),
    // isFrozen(address token, address account)
    ABI_ID_IS_FROZEN(0x46de0fb1),
    // freezeToken(address token, address account)
    ABI_ID_FREEZE(0x5b8f8584),
    // unfreezeToken(address token, address account)
    ABI_ID_UNFREEZE(0x52f91387),

    // **** HIP-358 function selectors ****
    // createFungibleToken(HederaToken memory token, uint initialTotalSupply, uint decimals)
    ABI_ID_CREATE_FUNGIBLE_TOKEN(0x7812a04b),
    // createFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  uint initialTotalSupply,
    //  uint decimals,
    //  FixedFee[] memory fixedFees,
    //  FractionalFee[] memory fractionalFees)
    ABI_ID_CREATE_FUNGIBLE_TOKEN_WITH_FEES(0x4c381ae7),
    // createNonFungibleToken(HederaToken memory token)
    ABI_ID_CREATE_NON_FUNGIBLE_TOKEN(0x9dc711e0),
    // createNonFungibleTokenWithCustomFees(
    //  HederaToken memory token,
    //  FixedFee[] memory fixedFees,
    //  RoyaltyFee[] memory royaltyFees)
    ABI_ID_CREATE_NON_FUNGIBLE_TOKEN_WITH_FEES(0x5bc7c0e6),

    // **** HIP-514 function selectors ****
    // getFungibleTokenInfo(address token)
    ABI_ID_GET_FUNGIBLE_TOKEN_INFO(0x3f28a19b),
    // getTokenInfo(address token)
    ABI_ID_GET_TOKEN_INFO(0x1f69565f),
    // getNonFungibleTokenInfo(address token, int64 serialNumber)
    ABI_ID_GET_NON_FUNGIBLE_TOKEN_INFO(0x287e1da8),
    // getTokenDefaultFreezeStatus(address token)
    ABI_ID_GET_TOKEN_DEFAULT_FREEZE_STATUS(0xa7daa18d),
    // getTokenDefaultKycStatus(address token)
    ABI_ID_GET_TOKEN_DEFAULT_KYC_STATUS(0x335e04c1),
    // isKyc(address token, address account)
    ABI_ID_IS_KYC(0xf2c31ff4),
    // 	grantTokenKyc(address  token, address account)
    ABI_ID_GRANT_TOKEN_KYC(0x8f8d7f99),
    // revokeTokenKyc(address token, address account)
    ABI_ID_REVOKE_TOKEN_KYC(0xaf99c633),
    // getTokenCustomFees(address token)
    ABI_ID_GET_TOKEN_CUSTOM_FEES(0xae7611a0);

    private final int functionSelector;

    PrecompileFunctionSelector(int functionSelector) {
        this.functionSelector = functionSelector;
    }

    public static PrecompileFunctionSelector fromFunctionId(int n) {
        for (PrecompileFunctionSelector c : values()) {
            if (c.functionSelector == n) {
                return c;
            }
        }
        return null;
    }

    public int getFunctionSelector() {
        return functionSelector;
    }
}
