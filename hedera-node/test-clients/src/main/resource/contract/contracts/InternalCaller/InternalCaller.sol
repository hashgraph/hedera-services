// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract InternalCaller {
    function callContract(address _addr) external {
        _addr.call(abi.encodeWithSignature("nonExisting()"));
    }
}
