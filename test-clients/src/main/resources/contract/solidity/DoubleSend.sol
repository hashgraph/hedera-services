pragma solidity ^0.5.3;

contract DoubleSend {
  event Target(address somebody);

  function() external payable {}
  constructor() public payable {}  

  function donate(uint32 toFirst, uint32 toSecond) public payable {
    address payable firstBeneficiary = address(uint120(toFirst));
    address payable secondBeneficiary = address(uint120(toSecond));
    emit Target(firstBeneficiary);
    emit Target(secondBeneficiary);
    firstBeneficiary.transfer(1);
    secondBeneficiary.transfer(1);
  }
}
