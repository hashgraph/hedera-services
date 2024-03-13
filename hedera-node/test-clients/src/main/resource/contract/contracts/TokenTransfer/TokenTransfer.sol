// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "@openzeppelin/contracts/token/ERC20/IERC20.sol";

contract TokenTransfer is HederaTokenService {

    event ResponseåCode(int responseCode);

    constructor() public payable {}

    fallback() external payable {}

    function transferTokenPublic(address token, address sender, address receiver, int64 amount) public returns (int responseCode) {
        responseCode = HederaTokenService.transferToken(token, sender, receiver, amount);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferNFTPublic(address token, address sender, address receiver, int64 serialNumber) public returns (int responseCode) {
        responseCode = HederaTokenService.transferNFT(token, sender, receiver, serialNumber);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function transferFromPublic(address token, address from, address to, uint256 amount) public returns (bool responseCode) {
        responseCode = IERC20(token).transferFrom(from, to, amount);

        if (responseCode != true) {
            revert ();
        }
    }

    function transferFromNFTPublic(address token, address from, address to, uint256 serialNumber) public returns (bool responseCode) {
        responseCode = IERC20(token).transferFrom(from, to, serialNumber);

        if (responseCode != true) {
            revert ();
        }
    }

    function approvePublic(address token, address spender, uint256 amount) public returns (int responseCode) {
        responseCode = HederaTokenService.approve(token, spender, amount);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function approveNFTPublic(address token, address approved, uint256 serialNumber) public returns (int responseCode) {
        responseCode = HederaTokenService.approveNFT(token, approved, serialNumber);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }
}
