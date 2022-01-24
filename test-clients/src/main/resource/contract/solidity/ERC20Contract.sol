// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract ERC20Contract {

    function balanceOf(address token, address account) public {
        IERC20(token).balanceOf(account);
    }
}