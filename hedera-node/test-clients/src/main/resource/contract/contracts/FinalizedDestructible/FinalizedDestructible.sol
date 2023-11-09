// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

contract FinalizedDestructible {
    function computeChildAddress(uint salt) public view returns (address) {
        bytes memory creationCode = type(Child).creationCode;
        bytes memory initcode = abi.encodePacked(creationCode, abi.encode(address(this))); 
        bytes32 hash = keccak256(abi.encodePacked(bytes1(0xff), address(this), salt, keccak256(initcode)));
        return address(uint160(uint(hash)));
    }

    function deployDeterministicChild(uint salt) external payable {
        bytes memory creationCode = type(Child).creationCode;
        bytes memory initcode = abi.encodePacked(creationCode, abi.encode(address(this))); 
        address addr;
        assembly {
            addr := create2(callvalue(), add(initcode, 0x20), mload(initcode), salt)
            if iszero(extcodesize(addr)) {
                revert(0, 0)
            }
        }
    }
}

contract Child {
    address public parent;

    constructor(address _parent) payable {
        parent = _parent;
    }

    function vacateAddress() external {
        selfdestruct(payable(parent));
    }
}
