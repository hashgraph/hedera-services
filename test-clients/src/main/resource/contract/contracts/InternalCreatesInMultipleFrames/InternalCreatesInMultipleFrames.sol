// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./ContractDeployer.sol";

contract InternalCreatesInMultipleFrames {

    ContractDeployer deployer;

    constructor() {
        deployer = new ContractDeployer();
    }

    function startDeploying(uint _numFrames, uint _deploysPerFrame) external {
        for(uint i=0; i<_numFrames; i++) {
            deployer.deployNTimes(_deploysPerFrame);
        }
    }

}