// SPDX-License-Identifier: MIT
pragma solidity <=0.8.10;
contract Callee {
    uint32 x;
    constructor() {
        x = 99;
    }
    function setX(uint32 _x) public {
        x = _x;
    }
    function getX() public view returns (uint32) {
        return x;
    }
}

