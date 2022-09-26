pragma solidity ^0.5.0;

contract Fallback {
  uint x;

  constructor() public payable {
    x = 2;
  }

  function() external {
    x = 1;
  }
}