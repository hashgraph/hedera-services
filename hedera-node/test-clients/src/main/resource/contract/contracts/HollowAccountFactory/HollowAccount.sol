// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

contract HollowAccount {
    address public creator;
    uint256 public creationTime;

    constructor(address _creator) {
        creator = _creator;
        creationTime = block.timestamp;
    }
}
