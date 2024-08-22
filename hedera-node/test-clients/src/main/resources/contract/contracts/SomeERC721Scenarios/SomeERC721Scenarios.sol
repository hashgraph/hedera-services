// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./ERC721/IERC721.sol";
import "./hip-206/HederaTokenService.sol";

contract SomeERC721Scenarios is HederaTokenService {
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
    ) external view returns (address) {
        return IERC721(token).getApproved(serialNo);
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
    ) external view returns (bool) {
        return IERC721(token).isApprovedForAll(owner, operator);
    }

    function setApprovalForAll(
        address token,
        address operator,
        bool approved
    ) external {
        IERC721(token).setApprovalForAll(operator, approved);
    }

    function transferFrom(
        address token,
        address from,
        address to,
        uint256 serialNo
    ) external {
        IERC721(token).transferFrom(from, to, serialNo);
    } 

    function nonSequiturMintAndTransfer(
        address token, 
        address recipient
    ) external {
        bytes[] memory metadata = new bytes[](2);
        metadata[0] = hex"ee"; 
        metadata[1] = hex"ff"; 
        (int rc, , int64[] memory sns) = HederaTokenService.mintToken(token, 0, metadata);
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, address(this), recipient, int64(sns[0]));
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, address(this), recipient, int64(sns[1]));
        require(rc == 22);
    }

    function nonSequiturMintAndTransferAndBurn(
        address token, 
        address recipient
    ) external {
        bytes[] memory metadata = new bytes[](2);
        metadata[0] = hex"ee"; 
        metadata[1] = hex"ff"; 
        address me = address(this);
        (int rc, , int64[] memory sns) = HederaTokenService.mintToken(token, 0, metadata);
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, me, recipient, int64(sns[0]));
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, me, recipient, int64(sns[1]));
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, recipient, me, int64(sns[0]));
        require(rc == 22);
        rc = HederaTokenService.transferNFT(token, recipient, me, int64(sns[1]));
        require(rc == 22);
        (rc, ) = HederaTokenService.burnToken(token, 0, sns);
    }
}
