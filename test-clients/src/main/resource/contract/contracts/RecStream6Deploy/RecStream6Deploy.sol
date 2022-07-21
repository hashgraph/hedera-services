// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./SmallContract.sol";
import "./AvgContract.sol";
import "./BigContract.sol";

contract RecStream6Deploy {

    function deploySmallContract(uint _num) external {
        for(uint i=0; i<_num; i++) {
            new SmallContract();
        }
    }

    function deployAverageContract(uint _num) external {
        for(uint i=0; i<_num; i++) {
            new AvgContract();
        }
    }

    function deployBigContract(uint _num) external {
        for(uint i=0; i<_num; i++) {
            new BigContract();
        }
    }

}