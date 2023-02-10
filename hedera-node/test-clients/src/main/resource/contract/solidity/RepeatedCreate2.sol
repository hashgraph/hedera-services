// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract RepeatedCreate2 {
    Target target;

    function createRepeatedCreator(bytes32 salt) public {
        target = new Target{salt: salt}();
        target.die();
        target = new Target{salt: salt}();
    }
}

contract Target {
    function die() public {
        address payable beneficiary = payable(msg.sender);
        selfdestruct(beneficiary);
    }
}

