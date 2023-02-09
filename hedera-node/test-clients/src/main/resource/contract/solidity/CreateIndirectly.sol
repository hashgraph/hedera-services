// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract Toy {
}

contract ToyMaker {
  function make() public returns (address) {
    return address(new Toy());
  }
}


