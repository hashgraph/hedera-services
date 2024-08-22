// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import "./IERC20.sol";
import "./IERC721.sol";
import "./IERC20Metadata.sol";
import "./IERC721Metadata.sol";
import "./IExchangeRate.sol";
import "./IPrngSystemContract.sol";

contract SpecialQueriesXTest {
    address constant XRATE_ADDRESS = address(0x168);
    address constant PRNG_ADDRESS = address(0x169);
    address constant HTS_ADDRESS = address(0x167);

    uint public secret;

    constructor(uint _secret) {
        secret = _secret;
    }

    function getTinycentsEquiv(uint tinybars) external returns (uint tinycents) {
        (bool success, bytes memory result) = XRATE_ADDRESS.call(
            abi.encodeWithSelector(IExchangeRate.tinybarsToTinycents.selector, tinybars));
        require(success);
        tinycents = abi.decode(result, (uint256));
    }
  
    function getPrngSeed() external returns (bytes32 entropy) {
        (bool success, bytes memory result) = PRNG_ADDRESS.call(
            abi.encodeWithSelector(IPrngSystemContract.getPseudorandomSeed.selector));
        require(success);
        entropy = abi.decode(result, (bytes32));
    }

    function getErc20Balance(address token, address account) external view returns (uint256 balance) {
        balance = IERC20(token).balanceOf(account);
    }

    function getErc20Supply(address token) external view returns (uint256 supply) {
        supply = IERC20(token).totalSupply();
    }

    function getErc20Name(address token) external view returns (string memory name) {
        name = IERC20Metadata(token).name();
    }

    function getErc20Symbol(address token) external view returns (string memory symbol) {
        symbol = IERC20Metadata(token).symbol();
    }

    function getErc20Decimals(address token) external view returns (uint8 decimals) {
        decimals = IERC20Metadata(token).decimals();
    }

    function getErc721Name(address token) external view returns (string memory name) {
        name = IERC721Metadata(token).name();
    }

    function getErc721Symbol(address token) external view returns (string memory symbol) {
        symbol = IERC721Metadata(token).symbol();
    }

    function getErc721TokenUri(address token, uint256 tokenId) external view returns (string memory tokenUri) {
        tokenUri = IERC721Metadata(token).tokenURI(tokenId);
    }

    function getErc721Balance(address token, address owner) external view returns (uint256 balance) {
        balance = IERC721Metadata(token).balanceOf(owner);
    }

    function getErc721Owner(address token, uint256 tokenId) external view returns (address owner) {
        owner = IERC721Metadata(token).ownerOf(tokenId);
    }

    function getErc721IsOperator(address token, address owner, address operator) external view returns (bool isOperator) {
        isOperator = IERC721Metadata(token).isApprovedForAll(owner, operator);
    }
}
