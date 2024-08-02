// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

contract ReverterConstructorTransfer {
    constructor() payable {
        payable(msg.sender).transfer(1);
    }
}
