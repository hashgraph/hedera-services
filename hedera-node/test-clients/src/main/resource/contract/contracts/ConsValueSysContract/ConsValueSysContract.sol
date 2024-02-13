// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract ConsValueSysContract {
    constructor() payable {
        (bool success,) = address(0x167).call{value: 1}("");

        require(success);
    }
}
