// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

interface IHRC904TokenCancel {
    function cancelAirdropFT(address receiverAddress) external returns (int64 responseCode);
    function cancelAirdropNFT(address receiverAddress, int64 serialNumber) external returns (int64 responseCode);
}

contract HRC904TokenCancel {
    function cancelAirdropFT(address token, address receiver) public returns (int64 responseCode) {
        return IHRC904TokenCancel(token).cancelAirdropFT(receiver);
    }

    function cancelAirdropNFT(address token, address receiver, int64 serial) public returns (int64 responseCode) {
        return IHRC904TokenCancel(token).cancelAirdropNFT(receiver, serial);
    }
}
