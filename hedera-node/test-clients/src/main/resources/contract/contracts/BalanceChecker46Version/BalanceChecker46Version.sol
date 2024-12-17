// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract BalanceChecker46Version {
    function balanceOf(address _address) public view returns (uint256) {
        return _address.balance;
    }
}
