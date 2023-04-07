// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract SafeHTS {

    address constant precompileAddress = address(0x167);
    // 90 days in seconds
    int32 constant defaultAutoRenewPeriod = 7776000;

    error CryptoTransferFailed();
    error MintFailed();
    error BurnFailed();
    error MultipleAssociationsFailed();
    error SingleAssociationFailed();
    error MultipleDissociationsFailed();
    error SingleDissociationFailed();
    error TokensTransferFailed();
    error NFTsTransferFailed();
    error TokenTransferFailed();
    error NFTTransferFailed();
    error CreateFungibleTokenFailed();
    error CreateFungibleTokenWithCustomFeesFailed();
    error CreateNonFungibleTokenFailed();
    error CreateNonFungibleTokenWithCustomFeesFailed();
    error ApproveFailed();
    error AllowanceFailed();
    error NFTApproveFailed();
    error GetApprovedFailed();
    error SetTokenApprovalForAllFailed();
    error IsApprovedForAllFailed();
    error IsFrozenFailed();
    error IsKYCGrantedFailed();
    error TokenDeleteFailed();
    error GetTokenCustomFeesFailed();
    error GetTokenDefaultFreezeStatusFailed();
    error GetTokenDefaultKYCStatusFailed();
    error GetTokenExpiryInfoFailed();
    error GetFungibleTokenInfoFailed();
    error GetTokenInfoFailed();
    error GetTokenKeyFailed();
    error GetNonFungibleTokenInfoFailed();
    error FreezeTokenFailed();
    error UnfreezeTokenFailed();
    error GrantTokenKYCFailed();
    error RevokeTokenKYCFailed();
    error PauseTokenFailed();
    error UnpauseTokenFailed();
    error WipeTokenAccountFailed();
    error WipeTokenAccountNFTFailed();
    error UpdateTokenInfoFailed();
    error UpdateTokenExpiryInfoFailed();
    error UpdateTokenKeysFailed();
    error IsTokenFailed();
    error GetTokenTypeFailed();

    function safeCreateNonFungibleToken(IHederaTokenService.HederaToken memory token) public payable returns
    (address tokenAddress){
        nonEmptyExpiry(token);
        int responseCode;
        (bool success, bytes memory result) = precompileAddress.call{value: msg.value}(
            abi.encodeWithSelector(IHederaTokenService.createNonFungibleToken.selector, token));
        (responseCode, tokenAddress) =
        success
        ? abi.decode(result, (int32, address))
        : (HederaResponseCodes.UNKNOWN, address(0));
        if (responseCode != HederaResponseCodes.SUCCESS) revert CreateNonFungibleTokenFailed();
    }

    function safeMintToken(address token, int64 amount, bytes[] memory metadata) public returns (int64 newTotalSupply, int64[] memory serialNumbers) {
        int32 responseCode;

        (bool success, bytes memory result) = precompileAddress.call(
            abi.encodeWithSelector(IHederaTokenService.mintToken.selector,
            token, amount, metadata));

        (responseCode, newTotalSupply, serialNumbers) = success ? abi.decode(result, (int32, int64, int64[])) : (HederaResponseCodes.UNKNOWN, int64(0), new int64[](0));

        if (responseCode != HederaResponseCodes.SUCCESS) revert MintFailed();
    }

    function tryDecodeSuccessResponseCode(bool success, bytes memory result) private pure returns (bool) {
       return (success ? abi.decode(result, (int32)) : HederaResponseCodes.UNKNOWN) == HederaResponseCodes.SUCCESS;
    }

    function nonEmptyExpiry(IHederaTokenService.HederaToken memory token) private view
    {
        if (token.expiry.second == 0 && token.expiry.autoRenewPeriod == 0) {
            token.expiry.autoRenewPeriod = defaultAutoRenewPeriod;
            token.expiry.autoRenewAccount = address(this);
        }
    }
}
