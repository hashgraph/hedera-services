// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.7;

contract EmitBlockTimestamp {
    event Time(uint now);
    event Hash(uint blockNo, bytes32 hash);

    function logNow() public {
        emit Time(block.timestamp);
        uint blockNo = block.number;
        bytes32 hash = blockhash(blockNo);
        emit Hash(blockNo, hash);
    }

    function getLastBlockHash() external view returns (bytes32) {
        return blockhash(block.number - 1);
    }

    function getLastBlockMeta() external view returns (
        uint bNo, 
        bytes32 bHash
    ) {
        bNo = block.number - 1; 
        bHash = blockhash(bNo);
    }
}
