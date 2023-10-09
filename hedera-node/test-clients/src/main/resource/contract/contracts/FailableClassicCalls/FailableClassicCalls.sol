// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract FailableClassicCalls {
    address constant HTS_SYSTEM_CONTRACT = address(0x167);

    function makeClassicCall(bytes memory data) external returns (bytes memory) {
        // No try-catch, so top-level status will be CONTRACT_REVERT_EXECUTED
        // if the call to the system contract reverts the calling frame
        (bool success, bytes memory output) = address(HTS_SYSTEM_CONTRACT).call(data);

        // Fail with CONTRACT_REVERT_EXECUTED (error message 0x4e487b71...01)
        assert(success);

        return output;
    }
}
