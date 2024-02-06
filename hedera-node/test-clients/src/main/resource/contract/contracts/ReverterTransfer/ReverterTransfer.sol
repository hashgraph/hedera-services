// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

contract ReverterTransfer {
    constructor() payable {
        address addr = address(bytes20(keccak256(abi.encode(block.timestamp))));
        payable(addr).transfer(1);
    }
}
