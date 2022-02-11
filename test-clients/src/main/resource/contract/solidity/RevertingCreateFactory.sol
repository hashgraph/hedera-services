// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

contract RevertingCreateFactory {
    // 1. Get bytecode of contract to be deployed
    function getBytecode(address _owner, uint _foo) public pure returns (bytes memory) {
        bytes memory bytecode = type(TestContract).creationCode;

        return abi.encodePacked(bytecode, abi.encode(_owner, _foo));
    }

    // 3. Deploy the contract
    function deploy(bytes memory bytecode) public payable {
        address addr;

        /*
        NOTE: How to call create

        create(v, p, n)
        create new contract with code at memory p to p + n
        and send v wei
        and return the new address
        */
        assembly {
            addr := create(callvalue(), add(bytecode, 0x20), mload(bytecode))
            // Solidity compiler automatically inserts this, but we can omit it...!!
            // if iszero(extcodesize(addr)) {
            //     revert(0, 0)
            // }
        }
    }
}

contract TestContract {
    address public owner;
    uint public foo;

    constructor(address _owner, uint _foo) payable {
        if (_foo > 10) {
            revert();
        }
        owner = _owner;
        foo = _foo;
    }

    function getBalance() public view returns (uint) {
        return address(this).balance;
    }

    function vacateAddress() public {
        selfdestruct(payable(owner));
    }
}
