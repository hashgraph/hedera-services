// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "./HollowAccount.sol";

contract HollowAccountFactory {
    event HollowAccountCreated(address indexed creator, address indexed hollowAccountAddress, bytes32 salt);

    function createHollowAccount(bytes32 salt) external returns (address) {
        address hollowAccountAddress;
        bytes memory bytecode = abi.encodePacked(type(HollowAccount).creationCode, abi.encode(msg.sender));

        assembly {
            hollowAccountAddress := create2(0, add(bytecode, 0x20), mload(bytecode), salt)
        }

        require(hollowAccountAddress != address(0), "Create2: Failed on deploy");

        emit HollowAccountCreated(msg.sender, hollowAccountAddress, salt);
        return hollowAccountAddress;
    }

    function getHollowAccountAddress(bytes32 salt) external view returns (address) {
        bytes memory bytecode = abi.encodePacked(type(HollowAccount).creationCode, abi.encode(msg.sender));
        bytes32 hash = keccak256(abi.encodePacked(
            bytes1(0xff),
            address(this),
            salt,
            keccak256(bytecode)
        ));
        return address(uint160(uint256(hash)));
    }

    function deployContract(bytes32 salt, bytes memory bytecode) external returns (address) {
        address deployedAddress;
        assembly {
            deployedAddress := create2(0, add(bytecode, 0x20), mload(bytecode), salt)
        }
        require(deployedAddress != address(0), "CREATE2: Failed on deploy");
        return deployedAddress;
    }
}
