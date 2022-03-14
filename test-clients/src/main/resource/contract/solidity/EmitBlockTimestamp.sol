// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract EmitBlockTimestamp {
    event Time(uint now);
    event Hash(uint blockNo, bytes32 hash);

    function logNow() public {
        emit Time(block.timestamp);
        uint prevBlockNo = block.number - 1;
        bytes32 prevHash = blockhash(prevBlockNo);
        emit Hash(prevBlockNo, prevHash);
    }
}
