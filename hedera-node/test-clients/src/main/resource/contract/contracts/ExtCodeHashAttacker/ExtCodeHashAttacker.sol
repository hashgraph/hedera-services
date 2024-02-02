// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

interface IExtCodeOperationsChecker {
    function hashOf(address _address) external view returns (bytes32 hash);
}

contract ExtCodeHashAttacker {

    // Function that attempts to call hashOf inside a loop, generating addresses on the fly
    function callHashOfInLoopWithTryCatch(
        address extCodeOperationsCheckerAddress,
        uint256 gasLimit,
        uint256 numberOfTries
    ) external {
        IExtCodeOperationsChecker checker = IExtCodeOperationsChecker(extCodeOperationsCheckerAddress);

        for (uint256 i = 0; i < numberOfTries; i++) {
            // These addresses are non-existent so, extcodehash might not be io intensive
            // A real attack would involve addresses in existence
            address generatedAddress = address(uint160(1000 + i));

            //control the gasLimit to leave 0 gas for extcodehash op
            try checker.hashOf{gas: gasLimit}(generatedAddress) returns (bytes32 result) {

            } catch {

            }
        }
    }
}

turn-off-allowCallsToNonContractAccounts
