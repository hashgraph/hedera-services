// SPDX-License-Identifier: MIT
pragma solidity ^0.8.24;

import {HederaTokenService} from "./HederaTokenService.sol";
import {HederaResponseCodes} from "./HederaResponseCodes.sol";

contract UpdateTokenMetadata is HederaTokenService {
    constructor(){
    }

    function callUpdateNFTsMetadata(address nftToken, int64[] memory serialNumbers, bytes memory _newMetadata) public {
        //todo: add comment on PR that I need opinions on this function's name, before opening PR in smart contracts repo.
        // I chose updateNFTsMetadata, because thats what the HAPI operation TokenUpdateNFTs does, if we think in the future,
        // We expect, this operation to be extended to do more stuff, we could possibly use tokenUpdateNFTs as well here.
        (int64 responseCode) = HederaTokenService.updateNFTsMetadata(nftToken, serialNumbers, _newMetadata);
        require(responseCode == HederaResponseCodes.SUCCESS, "Failed to update metadata for NFTs");
    }
}
