// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";
import "./IERC20.sol";

contract NestedCalls is HederaTokenService {

    function transferTokenCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
    }

    function transferTokensCall(address token, address[] memory accountIds, int64[] memory amounts) public {
        HederaTokenService.transferTokens(token, accountIds, amounts);
    }

    function transferNFTCall(address token, address sender, address receiver, int64 serialNum) public {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
    }

    function transferNFTsCall(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) public {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
    }
}

contract PrecompileAliasXfer is HederaTokenService {

    NestedCalls nested;

    constructor() {
        nested = new NestedCalls();
    }

    function transferNFTCall(address token, address sender, address receiver, int64 serialNum) public {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
    }

    function transferNFTThanRevertCall(address token, address sender, address receiver, int64 serialNum) public {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
        revert();
    }

    function transferNFTCallNestedThenAgain(address token, address sender, address receiver, int64 serialNum, int64 serialNum2) public {
        nested.transferNFTCall(token, sender, receiver, serialNum);
        HederaTokenService.transferNFT(token, sender, receiver, serialNum2);
    }

    function transferNFTsCall(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) public {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
    }

    function transferNFTsThanRevertCall(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) public {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        revert();
    }

    function transferNFTsCallNestedThenAgain(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber,  int64[] memory serialNumber2) public {
        nested.transferNFTsCall(token, sender, receiver, serialNumber);
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber2);
    }

    function transferTokenCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
    }

    function transferTokenThanRevertCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
        revert();
    }

    function transferTokenCallNestedThenAgain(address token, address sender, address receiver, int64 amount, int64 amount2) public {
        nested.transferTokenCall(token, sender, receiver, amount);
        HederaTokenService.transferToken(token, sender, receiver, amount2);
    }

    function transferTokensCall(address token, address[] memory accountIds, int64[] memory amounts) public {
        HederaTokenService.transferTokens(token, accountIds, amounts);
    }

    function transferTokensThanRevertCall(address token, address[] memory accountIds, int64[] memory amounts) public {
        HederaTokenService.transferTokens(token, accountIds, amounts);
        revert();
    }

    function transferTokensCallNestedThenAgain(address token, address[] memory accountIds, int64[] memory amounts,  int64[] memory amounts2) public {
        nested.transferTokensCall(token, accountIds, amounts);
        HederaTokenService.transferTokens(token, accountIds, amounts2);
    }

}
