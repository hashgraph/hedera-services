// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/IHederaTokenService.sol";

contract AutoCreationModes {
    event Log(string message);

    address constant HTS_ENTRY_POINT = address(0x167);

    function createIndirectlyRevertingAndRecover(address token, address sender, address receiver, int64 serialNum) public {
        try this.createDirectly(token, sender, receiver, serialNum, true) {
        } catch Error(string memory reason) {
            emit Log(reason);
        }
    }

    function createDirectly(address token, address sender, address receiver, int64 serialNum, bool revertAfter) public {
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.transferNFT.selector, token, sender, receiver, serialNum));
        require(success);
        int32 rc = abi.decode(result, (int32));
        require(rc == 22);
        if (revertAfter) {
            revert("Wish, command, etc.");
        }
    }

    function createDirectlyViaFungible(address token, address sender, address receiver, int64 amount) public {
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.transferToken.selector, token, sender, receiver, amount));
        require(success);
        int32 rc = abi.decode(result, (int32));
        require(rc == 22);
    }

    function createSeveralDirectly(
        address token, 
        address[] memory senders, 
        address[] memory receivers, 
        int64[] memory serialNums
    ) public {
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.transferNFTs.selector, token, senders, receivers, serialNums));
        require(success);
        int32 rc = abi.decode(result, (int32));
        require(rc == 22);
    }
}
