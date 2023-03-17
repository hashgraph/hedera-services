// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./IERC20.sol";
import "./HederaTokenService.sol";

contract SomeERC20Scenarios {

    function doSpecificApproval(
        address token,
        address spender,
        uint256 amount
    ) external {
        IERC20(token).approve(spender, amount);
    }

    function doTransferFrom(
        address token,
        address from,
        address to,
        uint256 amount
    ) external {
        IERC20(token).transferFrom(from, to, amount);
    }

    function getAllowance(
        address token,
        address owner,
        address spender
    ) external view {
        IERC20(token).allowance(owner, spender);
    }

    function approveAndGetAllowanceAmount(
        address token,
        address spender,
        uint256 amount
    ) external {
        address me = address(this);
        IERC20(token).approve(spender, amount);
        IERC20(token).allowance(me, spender);
    }
}
