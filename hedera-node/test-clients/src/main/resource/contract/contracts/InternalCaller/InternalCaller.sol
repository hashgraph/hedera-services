// SPDX-License-Identifier: UNLICENSED
pragma solidity ^0.8.0;

contract InternalCaller {
    constructor() payable {}
    function callContract(address _addr) external {
        _addr.call(abi.encodeWithSignature("nonExisting()"));
    }

    function callTransfer(address _addr) external {
        _addr.transfer(1);
    }

    function callWithValue(address _addr) external {
        _addr.call{value: 1}("");
    }
}
