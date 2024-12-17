// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "./HederaTokenService.sol";
import "./HederaResponseCodes.sol";

contract HtsApproveAllowance is HederaTokenService {

    function htsApprove(address token, address spender, uint256 amount) public returns (bool success) {
        return HederaTokenService.approve(token, spender, amount) == HederaResponseCodes.SUCCESS;
    }

    function htsAllowance(address token, address owner, address spender) public returns (uint256 amount){
        int _responseCode;
        (_responseCode, amount) = HederaTokenService.allowance(token, owner, spender);
    }

    function htsApproveNFT(address token, address spender, uint256 serialNumber) public returns (bool success) {
        bytes memory result;
        (success, result) = precompileAddress.delegatecall(
            abi.encodeWithSelector(IHederaTokenService.approveNFT.selector,
            token, spender, serialNumber));
    }

    function htsGetApproved(address token, uint256 serialNumber) public returns (address approved) {
        int _responseCode;
        (_responseCode, approved) = HederaTokenService.getApproved(token, serialNumber);
    }

    function htsSetApprovalForAll(address token, address spender, bool approved) public returns (bool success) {
        return HederaTokenService.setApprovalForAll(token, spender, approved) == HederaResponseCodes.SUCCESS;
    }

    function htsIsApprovedForAll(address token, address owner, address operator) public returns (bool approved) {
        int _responseCode;
        (_responseCode, approved) = HederaTokenService.isApprovedForAll(token, owner, operator);
    }

}
