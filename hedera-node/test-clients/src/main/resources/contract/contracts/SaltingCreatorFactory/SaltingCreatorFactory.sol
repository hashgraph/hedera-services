// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract SaltingCreatorFactory {
    function buildCreator(bytes32 salt) public {
        uint primal_foo = 42;

        address predictedAddress = address(uint160(uint(keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                salt,
                keccak256(abi.encodePacked(
                    type(SaltingCreator).creationCode,
                    primal_foo
                ))
            )))));

        SaltingCreator creator = new SaltingCreator{salt: salt}(primal_foo);
        require(address(creator) == predictedAddress);
    }

    function callCreator(address creator_address, bytes32 salt) public {
        SaltingCreator creator = SaltingCreator(creator_address);
        creator.createSaltedTestContract(salt);
    }
}

contract SaltingCreator {
    uint public primal_foo;

    event Creation(uint v);

    constructor(uint _primal_foo) payable {
        primal_foo = _primal_foo;
    }

    function createSaltedTestContract(bytes32 salt) public {
        new TestContract{salt: salt}(address(this), primal_foo);
        emit Creation(primal_foo);
    }

    function createAndRecreateTest(bytes32 salt) public {
        TestContract tbd = new TestContract{salt: salt}(address(this), primal_foo);
        tbd.vacateAddress();
        new TestContract{salt: salt}(address(this), primal_foo);
    }

    function whatTheFoo() public view returns (uint) {
        return primal_foo;
    }
}

contract TestContract {
    address public owner;
    uint public foo;

    constructor(address _owner, uint _foo) payable {
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
