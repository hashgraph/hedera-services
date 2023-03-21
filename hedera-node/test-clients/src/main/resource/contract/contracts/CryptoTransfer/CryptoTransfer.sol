// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./IHederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract CryptoTransfer {
    IHederaTokenService constant HTS = IHederaTokenService(address(0x167));

    constructor() public {
    }

    function transferMultipleTokens(IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        int response = HTS.cryptoTransfer(tokenTransfers);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Crypto Transfer Failed");
        }
    }
}