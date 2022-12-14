// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./HederaTokenService.sol";
import "./ExpiryHelper.sol";
import "./KeyHelper.sol";

contract TokenTransferContract is HederaTokenService, ExpiryHelper, KeyHelper {

    event ResponseCode(int responseCode);

    function cryptoTransferPublic(IHederaTokenService.TransferList calldata transferList, IHederaTokenService.TokenTransferList[] calldata tokenTransferList) public returns (int responseCode) {
        responseCode = HederaTokenService.cryptoTransfer(transferList, tokenTransferList);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function delegateCryptoTransferPublic(IHederaTokenService.TransferList calldata transferList, IHederaTokenService.TokenTransferList[] calldata tokenTransferList) public returns (int responseCode) {
        responseCode = HederaTokenService.delegateCryptoTransfer(transferList, tokenTransferList);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function transferTokensPublic(address token, address[] memory accountId, int64[] memory amount) external returns (int256 responseCode) {
        responseCode = HederaTokenService.transferTokens(token, accountId, amount);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function transferNFTsPublic(address token, address[] memory sender, address[] memory receiver, int64[] memory serialNumber) external returns (int256 responseCode) {
        responseCode = HederaTokenService.transferNFTs(token, sender, receiver, serialNumber);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

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

    function transferFromPublic(address token, address from, address to, uint256 amount) public returns (int64 responseCode) {
        responseCode = this.transferFrom(token, from, to, amount);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function transferFromNFTPublic(address token, address from, address to, uint256 serialNumber) public returns (int64 responseCode) {
        responseCode = this.transferFromNFT(token, from, to, serialNumber);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function setApprovalForAllPublic(address token, address operator, bool approved) public returns (int responseCode) {
        responseCode = HederaTokenService.setApprovalForAll(token, operator, approved);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function delegateSetApprovalForAllPublic(address token, address operator, bool approved) public returns (int responseCode) {
        responseCode = HederaTokenService.delegateSetApprovalForAll(token, operator, approved);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert();
        }
    }

    function approvePublic(address token, address spender, uint256 amount) public returns (int responseCode) {
    responseCode = HederaTokenService.approve(token, spender, amount);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }

    function delegateApprovePublic(address token, address spender, uint256 amount) public returns (int responseCode) {
        responseCode = HederaTokenService.delegateApprove(token, spender, amount);
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

    function delegateApproveNFTPublic(address token, address approved, uint256 serialNumber) public returns (int responseCode) {
        responseCode = HederaTokenService.delegateApproveNFT(token, approved, serialNumber);
        emit ResponseCode(responseCode);

        if (responseCode != HederaResponseCodes.SUCCESS) {
            revert ();
        }
    }
}
