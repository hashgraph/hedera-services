// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.6.12;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract ZenosBank {

    address tokenAddress;

    uint256 lastWithdrawalTime;

    int64 deposited;

    constructor(address _tokenAddress) public {
        tokenAddress = _tokenAddress;
    }

    function depositTokens(int64 amount) public {
        int response = HederaTokenService.transferToken(tokenAddress, msg.sender, address(this), amount);
        if (response == 22) {//FIXME HederaResponseCodes.SUCCESS) {
            deposited += amount;
        } else {
            revert (int2str(response));
        }
    }

    function withdrawTokens() external {
        if (block.timestamp > lastWithdrawalTime) {
            HederaTokenService.associateToken(msg.sender, tokenAddress);
            depositTokens(- deposited / 2);
            lastWithdrawalTime = block.timestamp;
        } else {
            revert("Already withdrew this second");
        }
    }

    function int2str(int _i) internal pure returns (string memory _uintAsString) {
        if (_i == 0) {
            return "0";
        }
        int j = _i;
        uint len;
        while (j != 0) {
            len++;
            j /= 10;
        }
        bytes memory bstr = new bytes(len);
        uint k = len - 1;
        while (_i != 0) {
            bstr[k--] = byte(uint8(48 + _i % 10));
            _i /= 10;
        }
        return string(bstr);
    }
}