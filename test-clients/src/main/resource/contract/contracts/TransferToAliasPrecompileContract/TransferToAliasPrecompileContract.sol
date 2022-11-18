// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract TransferToAliasPrecompileContract is HederaTokenService {

    function transferNFTCall(address token, address sender, address receiver, int64 serialNum) public {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
    }

    function transferNFTThanRevertCall(address token, address sender, address receiver, int64 serialNum) public {
        HederaTokenService.transferNFT(token, sender, receiver, serialNum);
        revert();
    }

    function transferNFTsCall(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) public {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
    }

    function transferNFTsThanRevertCall(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) public {
        HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        revert();
    }

    function transferTokenCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
    }

    function transferTokenThanRevertCall(address token, address sender, address receiver, int64 amount) public {
        HederaTokenService.transferToken(token, sender, receiver, amount);
        revert();
    }

    function transferTokensCall(address token, address[] memory accountIds, int64[] memory amounts) public {
        HederaTokenService.transferTokens(token, accountIds, amounts);
    }

    function transferTokensThanRevertCall(address token, address[] memory accountIds, int64[] memory amounts) public {
        HederaTokenService.transferTokens(token, accountIds, amounts);
        revert();
    }
}