// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract DoubleSend {
    event Target(address somebody);

    // function() external payable {}
    constructor() payable {}

    function donate(address toFirst, address toSecond) public payable {
        address payable firstBeneficiary = payable(toFirst);
        address payable secondBeneficiary = payable(toSecond);
        emit Target(firstBeneficiary);
        emit Target(secondBeneficiary);
        firstBeneficiary.transfer(1);
        secondBeneficiary.transfer(1);
    }
}
