// SPDX-License-Identifier: Apache-2.0
pragma solidity ^0.8.0;

import "IERC721.sol";

contract ApproveByDelegateCall {
    function doIt(address token, address to, uint256 tokenId) public {
        (bool success, bytes memory result) = 
            address(IERC721(token)).delegatecall(
                abi.encodeWithSignature(
                    "approve(address,uint256)", 
                    to, tokenId));
        require(success);
    }
}
