// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

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

    function lowLevelECREC() external payable {
        bytes32 hash = bytes32("hash");
        uint8 v = uint8(1);
        bytes32 r = bytes32("r");
        bytes32 s = bytes32("s");
        (bool success, ) = address(0x01).call(
            abi.encodeWithSignature("ecrecover(bytes32,uint8,bytes32,bytes32)", hash, v, r, s));    
        require(success);
    }

    function lowLevelECRECWithValue() external payable {
        bytes32 hash = bytes32("hash");
        uint8 v = uint8(1);
        bytes32 r = bytes32("r");
        bytes32 s = bytes32("s");
        (bool success, ) = address(0x01).call{value: 1}(
            abi.encodeWithSignature("ecrecover(bytes32,uint8,bytes32,bytes32)", hash, v, r, s));
        require(success);
    }
}
