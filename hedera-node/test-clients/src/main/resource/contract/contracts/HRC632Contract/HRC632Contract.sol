// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaAccountService.sol";
import "./IHederaAccountService.sol";
import "./HederaResponseCodes.sol";

contract HRC632Contract is HederaAccountService {

    function hbarAllowanceCall(address owner, address spender) external returns (int64 responseCode, int256 amount)
    {
        (responseCode, amount) = HederaAccountService.hbarAllowance(owner, spender);
        require(responseCode == HederaResponseCodes.SUCCESS, "Hbar allowance failed");
    }

    function hbarApproveCall(address owner, address spender, int256 amount) external returns (int64 responseCode)
    {
        responseCode = HederaAccountService.hbarApprove(owner, spender, amount);
        require(responseCode == HederaResponseCodes.SUCCESS, "Hbar approve failed");
    }
}