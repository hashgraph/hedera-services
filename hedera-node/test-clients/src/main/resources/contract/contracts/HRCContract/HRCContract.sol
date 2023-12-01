// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC {
    function associate() external returns (uint256 responseCode);
    function dissociate() external returns (uint256 responseCode);
}

contract HRCContract {
    function associate(address token) public returns (uint256 responseCode) {
        return IHRC(token).associate();
    }

    function dissociate(address token) public returns (uint256 responseCode) {
        return IHRC(token).dissociate();
    }
}