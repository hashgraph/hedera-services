// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract RepeatedSelfDestructions {
    function repeatWith(bytes32 salt) public {
        new SelfDestroyer{salt: salt}();
        new SelfDestroyer{salt: salt}();
    }
}

contract SelfDestroyer {
    constructor() {
        address payable beneficiary = payable(msg.sender);
        selfdestruct(beneficiary);
    }
}

