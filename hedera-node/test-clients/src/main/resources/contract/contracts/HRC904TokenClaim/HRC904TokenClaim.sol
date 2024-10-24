// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC904TokenClaim {
    function claimAirdropFT(address senderAddress) external returns (int64 responseCode);
    function claimAirdropNFT(address senderAddress, int64 serialNumber) external returns (int64 responseCode);
}

contract HRC904TokenClaim is IHRC904TokenClaim{
    function claimAirdropFT(address sender) public returns (int64 responseCode) {
        return IHRC904TokenClaim(this).claimAirdropFT(sender);
    }

    function claimAirdropNFT(address sender, int64 serial) public returns (int64 responseCode) {
        return IHRC904TokenClaim(this).claimAirdropNFT(sender, serial);
    }
}