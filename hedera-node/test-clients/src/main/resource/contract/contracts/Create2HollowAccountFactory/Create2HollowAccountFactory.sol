// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

contract Create2HollowAccountFactory {
    event ContractDeployed(address indexed deployer, address indexed contractAddress, bytes32 salt);

    function deployContract(bytes32 salt, bytes memory bytecode) external returns (address) {
        address deployedAddress;
        assembly {
            deployedAddress := create2(0, add(bytecode, 0x20), mload(bytecode), salt)
        }
        require(deployedAddress != address(0), "CREATE2: Failed on deploy");
        emit ContractDeployed(msg.sender, deployedAddress, salt);
        return deployedAddress;
    }

    function getDeploymentAddress(bytes32 salt, bytes memory bytecode) external view returns (address) {
        bytes32 hash = keccak256(abi.encodePacked(
            bytes1(0xff),
            address(this),
            salt,
            keccak256(bytecode)
        ));
        return address(uint160(uint256(hash)));
    }
}
