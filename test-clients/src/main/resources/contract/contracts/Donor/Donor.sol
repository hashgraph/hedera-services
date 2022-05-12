// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract Donor {
    constructor() payable {
    }

    function relinquishFundsTo(address beneficiary) public {
        selfdestruct(payable(beneficiary));
    }
}
