// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.7;

contract ChainIdUser {
    uint256 savedChainId;

    constructor() {
        uint256 id;
        assembly {
            id := chainid()
        }
        savedChainId = id;
    }

    function getChainID() external view returns (uint256) {
        uint256 id;
        assembly {
            id := chainid()
        }
        return id;
    }

    function getSavedChainID() external view returns (uint256) {
        return savedChainId;
    }
}
