pragma solidity ^0.5.3;

contract LastTrackingSender {
  uint32 lastSent = 0;

  function() external payable {}
  constructor() public payable {}  

  function howMuch() public view returns (uint32) {
    return lastSent;
  }

  function uncheckedTransfer(uint32 toNum, uint32 amount) public payable {
    lastSent = amount;
    address payable beneficiary = address(uint120(toNum));
    beneficiary.transfer(amount);
  }
}
