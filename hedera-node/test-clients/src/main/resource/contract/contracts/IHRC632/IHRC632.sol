// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC632 {
    function hbarAllowance(address spender) external returns (int64 responseCode, int256 allowance);
    function hbarApprove(address spender, int256 amount) external returns (int64 responseCode);
}
