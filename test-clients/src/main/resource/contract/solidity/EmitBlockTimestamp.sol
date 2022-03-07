// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract EmitBlockTimestamp {
    event Time(uint now);

    function logNow() public {
        emit Time(block.timestamp);
    }
}
