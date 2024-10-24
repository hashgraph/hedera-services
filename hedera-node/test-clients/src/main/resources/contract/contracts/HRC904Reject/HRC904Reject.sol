// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC904TokenReject {
    function rejectTokenFT() external returns (int64 responseCode);
    function rejectTokenNFTs(int64[] memory serialNumbers) external returns (int64 responseCode);
}

contract HRC904TokenReject is IHRC904TokenReject {
    function rejectTokenFT() public returns (int64 responseCode) {
        return HRC904TokenReject(this).rejectTokenFT();
    }

    function rejectTokenNFTs(int64[] memory serialNumbers) public returns (int64 responseCode) {
        return HRC904TokenReject(this).rejectTokenNFTs(serialNumbers);
    }
}