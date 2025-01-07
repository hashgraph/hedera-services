// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "./HederaTokenService.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/token/ERC20/extensions/IERC20Metadata.sol";

contract RedirectTestContract is HederaTokenService {

    function getBalanceOf(address token, address account) public returns (bytes memory result) {
        (int response, bytes memory result) = HederaTokenService.redirectForToken(token, abi.encodeWithSelector(IERC20.balanceOf.selector, account));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token redirect failed");
        }
        return result;
    }

    function decimalsRedirect(address token) public returns (bytes memory result) {
        (int response, bytes memory responseResult) = this.redirectForToken(token, abi.encodeWithSelector(IERC20Metadata.decimals.selector));
        if (response != HederaResponseCodes.SUCCESS) {
            revert ("Token decimals() redirect failed");
        }
        return responseResult;
    }
}