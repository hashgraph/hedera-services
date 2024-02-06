// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

contract ReverterConstructorCallWithValueToHederaPrecompile {
    constructor() payable {
        (bool success,) = address(0x167).call{value: 1}("");

        require(success);
    }
}
