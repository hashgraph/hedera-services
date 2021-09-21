// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.0;

contract Test {
    uint public pos0;

    function set(uint n) public {
        pos0 = n;
    }
}