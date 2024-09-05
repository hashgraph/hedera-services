// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./IERC721.sol";
import "./IERC721Metadata.sol";
import "./hip-206/IHederaTokenService.sol";

contract ErcAndHtsAlternatives {
    address constant HTS_ENTRY_POINT = address(0x167);

    function canGetMetadataViaERC(address token, uint256 serialNo) external view returns (bytes memory) {
        return bytes(IERC721Metadata(token).tokenURI(serialNo));
    }

    function canGetMetadataViaHTS(address token, uint256 serialNo) external returns (bytes memory) {
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.getNonFungibleTokenInfo.selector, token, int64(uint64(serialNo))));
        require(success);
        (int32 rc, IHederaTokenService.NonFungibleTokenInfo memory info) = 
            abi.decode(result, (int32, IHederaTokenService.NonFungibleTokenInfo));
        require(rc == 22);
        return info.metadata;
    }
}
