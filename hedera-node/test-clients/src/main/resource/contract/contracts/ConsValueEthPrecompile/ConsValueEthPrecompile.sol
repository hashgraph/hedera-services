// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract ConsValueEthPrecompile {
    constructor() payable {
        (bool success,) = address(0x2).call{value: 1}("");

        require(success);
    }
}
