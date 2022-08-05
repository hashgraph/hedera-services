// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

contract ContractDeployer {

    function deployNTimes(uint _num) external {
        for(uint i=0; i<_num; i++) {
            new SmallContract();
        }
    }

}

contract SmallContract {

}