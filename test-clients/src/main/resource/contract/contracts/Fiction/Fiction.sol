// SPDX-License-Identifier: MIT
pragma solidity ^0.8.9;

import './hip-206/HederaTokenService.sol';
import './hip-206/HederaResponseCodes.sol';
import './hip-206/IHederaTokenService.sol';
import "./ERC721/IERC721.sol";

contract Fiction {
    function transferFrom(
        address token,
        address from,
        address to,
        uint256 serialNo
    ) external {
        IERC721(token).transferFrom(from, to, serialNo);
    } 

    function okThen(
        address token,
        address sender,
        address receiver
    ) external {
        address(0x167).call(
            abi.encodeWithSelector(
                IHederaTokenService.transferNFT.selector, 
                token, sender, receiver, int64(1)));
    }

    function collectFallbackFee(
        address nfToken, 
        address owner,
        address receiver
    ) external {
        Sender sender = new Sender();
        sender.sendSN1(nfToken, owner, receiver);
        require(true == false);
    }
}

contract Sender is HederaTokenService {
    function sendSN1(
        address nfToken, 
        address sender,
        address receiver
    ) external {
        int rc = HederaTokenService.transferNFT(
            nfToken, sender, receiver, int64(1));
        require(rc == HederaResponseCodes.SUCCESS);
    }
}
