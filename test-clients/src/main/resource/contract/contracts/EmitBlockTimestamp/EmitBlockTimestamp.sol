// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.7;

contract EmitBlockTimestamp {
    event Time(uint now);
    event Hash(uint blockNo, bytes32 hash);

    function logNow() public {
        emit Time(block.timestamp);
        uint blockNo = block.number;
        bytes32 prevHash = blockhash(blockNo);
        emit Hash(blockNo, prevHash);
    }
}