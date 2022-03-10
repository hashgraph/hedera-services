// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract CreateDonor {
    function buildDonor(bytes32 salt) public payable {
        address predictedAddress = address(uint160(uint(keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                salt,
                keccak256(abi.encodePacked(
                    type(Donor).creationCode
                ))
            )))));

        Donor donor = new Donor{salt: salt, value: 100}();
        require(address(donor) == predictedAddress);
    }

    function buildThenRevert(bytes32 salt) public payable {
        new Donor{salt: salt, value: 100}();
        revert("NOPE");
    }

    function buildThenRevertThenBuild(bytes32 salt) public payable {
        try this.buildThenRevert{value: 100}(salt) {
            /* No-op */
        } catch Error(string memory) {
        }
        new Donor{salt: salt, value: 100}();
    }
}

contract Donor {
    constructor() payable {
    }

    function relinquishFundsTo(address beneficiary) public {
        selfdestruct(payable(beneficiary));
    }
}
