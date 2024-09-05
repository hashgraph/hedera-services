// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.0;

contract ThreeSlots {
    uint a;
    uint b;
    uint c;

    function setAB(uint _a, uint _b) external {
      a = _a;
      b = _b;
    }

    function setC(uint _c) external {
      c = _c;
    }
}
