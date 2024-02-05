// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract InvalidFunctionSelector {
    address constant HTS_ENTRY_ADDRESS = address(0x167);

    function callWith(bytes memory input) external{
        (bool result, ) = HTS_ENTRY_ADDRESS.call(input);
        require(result, "Call failed");
    }
}