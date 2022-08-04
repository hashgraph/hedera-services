// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;
import "./FeeHelper.sol";
import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/ERC20.sol";
import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";

contract UpdateTokenInfoContract is HederaTokenService, FeeHelper {
    string name = "tokenName";
    string symbol = "tokenSymbol";
    string memo = "memo";

    function tokenInfoUpdate(address token, IHederaTokenService.HederaToken memory tokenInfo)external{
        int response = HederaTokenService.updateTokenInfo(token,tokenInfo);

        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Update of tokenInfo failed!");
        }
    }
    // TEST-001
    function updateTokenWithKeysAndExpiry(
        address tokenID,
        address treasury,
        bytes memory ed25519,
        bytes memory ecdsa,
        address contractID,
        address delegatableContractID,
        address autoRenewAccount,
        uint32 autoRenewPeriod,
        address toAssociateWith
    ) public payable {
        IHederaTokenService.TokenKey[] memory keys = new IHederaTokenService.TokenKey[](5);
        keys[0] = getSingleKey(0, 1, 3, ed25519);
        keys[1] = getSingleKey(2, 3, 4, ecdsa);
        keys[2] = getSingleKey(4, 2, contractID);
        keys[3] = getSingleKey(5, 5, delegatableContractID);
        keys[4] = getSingleKey(6, 1, "");

        IHederaTokenService.HederaToken memory token =
        createTokenWithExpiry(treasury, 0, autoRenewAccount, autoRenewPeriod, keys);

        int responseCode = HederaTokenService.updateTokenInfo(tokenID,token);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ("Update of tokenInfo failed!");
        }
    }

    /** --- HELPERS --- */

    function createTokenWithExpiry(
        address treasury,
        uint32 second,
        address autoRenewAccount,
        uint32 autoRenewPeriod,
        IHederaTokenService.TokenKey[] memory keys
    ) internal returns (IHederaTokenService.HederaToken memory token) {

        IHederaTokenService.Expiry memory expiry;
        expiry.second = second;
        expiry.autoRenewAccount = autoRenewAccount;
        expiry.autoRenewPeriod = autoRenewPeriod;

        token.name = name;
        token.symbol = symbol;
        token.treasury = treasury;
        token.tokenKeys = keys;
        token.expiry = expiry;
        token.memo = memo;
    }
}