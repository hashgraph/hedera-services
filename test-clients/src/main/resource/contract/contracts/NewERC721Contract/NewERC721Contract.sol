// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "@openzeppelin/contracts/token/ERC721/IERC721.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Metadata.sol";
import "@openzeppelin/contracts/token/ERC721/extensions/IERC721Enumerable.sol";

contract ERC721Contract {

    address token;

//    constructor(address tokenAddress) {
    ////        token = tokenAddress;
    ////    }

    function setAddress(address tokenAddress) external {
        token = tokenAddress;
    }

    function balanceOf(address owner) external view returns (uint256) {
        return IERC721(token).balanceOf(owner);
    }

    function ownerOf(uint256 tokenId) external view returns (address) {
        return IERC721(token).ownerOf(tokenId);
    }

    function name() public view returns (string memory) {
        return IERC721Metadata(token).name();
    }

    function symbol() public view returns (string memory) {
        return IERC721Metadata(token).symbol();
    }

    function tokenURI(uint256 tokenId) public view returns (string memory) {
        return IERC721Metadata(token).tokenURI(tokenId);
    }

    function totalSupply() external view returns (uint256) {
        return IERC721Enumerable(token).totalSupply();
    }

    // The `to` address will receive approval by the contract itself
    // Be aware that the nft must be owned by the contract, not by the msg.sender address
    function approve(address to, uint256 tokenId) external payable {
        IERC721(token).approve(to, tokenId);
    }

    // The `to` address will receive approval by msg.sender
    function delegateApprove(address to, uint256 tokenId) external payable {
        address(IERC721(token)).delegatecall(abi.encodeWithSignature("approve(address,uint256)", to, tokenId));
    }

    function setApprovalForAll(address operator, bool approved) external {
        IERC721(token).setApprovalForAll(operator, approved);
    }

    function getApproved(uint256 tokenId) external view returns (address) {
        return IERC721(token).getApproved(tokenId);
    }

    function isApprovedForAll(address owner, address operator) public view returns (bool) {
        return IERC721(token).isApprovedForAll(owner, operator);
    }

    // The call will be executed by the contract itself, so the contract address has to be the owner of `tokenId`
    function transferFrom(address from, address to, uint256 tokenId) external payable {
        IERC721(token).transferFrom(from, to, tokenId);
    }

    // The call will be executed by the msg.sender address
    function delegateTransferFrom(address from, address to, uint256 tokenId) external payable {
        address(IERC721(token)).delegatecall(abi.encodeWithSignature("transferFrom(address,address,uint256)", from, to, tokenId));
    }

    // Not supported operations - should return a failure

    function safeTransferFrom(address from, address to, uint256 tokenId) external payable {
        IERC721(token).safeTransferFrom(from, to, tokenId);
    }

    function safeTransferFromWithData(address from, address to, uint256 tokenId, bytes calldata data) external payable {
        IERC721(token).safeTransferFrom(from, to, tokenId, data);
    }

    function tokenByIndex(uint256 index) external view returns (uint256) {
        return IERC721Enumerable(token).tokenByIndex(index);
    }

    function tokenOfOwnerByIndex(address owner, uint256 index) external view returns (uint256) {
        return IERC721Enumerable(token).tokenOfOwnerByIndex(owner, index);
    }
}
