// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./ERC721/IERC721.sol";

contract SomeERC721Scenarios {
    function iMustOwnAfterReceiving(
        address token, 
        uint256 serialNo
    ) external {
        address me = address(this);
        IERC721(token).transferFrom(me, msg.sender, serialNo);
        address owner = IERC721(token).ownerOf(serialNo);
        require(owner == msg.sender);
    } 

    function revokeSpecificApproval(
        address token, 
        uint256 serialNo
    ) external {
        IERC721(token).approve(address(0), serialNo);
    } 

    function doSpecificApproval(
        address token, 
        address spender, 
        uint256 serialNo
    ) external {
        IERC721(token).approve(spender, serialNo);
    }

    function getApproved(
        address token,
        uint256 serialNo
    ) external view {
        IERC721(token).getApproved(serialNo);
    }

    function getBalanceOf(
        address token,
        address owner
    ) external view {
        IERC721(token).balanceOf(owner);
    }

    function getOwnerOf(
        address token,
        uint256 serialNo
    ) external view {
        IERC721(token).ownerOf(serialNo);
    }

    function isApprovedForAll(
        address token,
        address owner,
        address operator
    ) external view {
        IERC721(token).isApprovedForAll(owner, operator);
    }
}
