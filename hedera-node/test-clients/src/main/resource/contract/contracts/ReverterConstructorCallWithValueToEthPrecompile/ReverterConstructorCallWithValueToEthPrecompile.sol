// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.20;

contract ReverterConstructorCallWithValueToEthPrecompile {
    constructor() payable {
        (bool success,) = address(0x2).call{value: 1}("");

        require(success);
    }
}
