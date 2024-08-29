// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/HederaTokenService.sol";

contract NonDelegateCryptoTransfer is HederaTokenService {

    constructor() public {
    }

    function transferMultipleTokens(IHederaTokenService.TokenTransferList[] memory tokenTransfers) external {
        int response = HederaTokenService.cryptoTransfer(tokenTransfers);
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Crypto Transfer Failed");
        }
    }
}
