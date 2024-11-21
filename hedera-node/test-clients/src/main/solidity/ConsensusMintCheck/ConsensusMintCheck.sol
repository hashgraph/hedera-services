// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;
pragma experimental ABIEncoderV2;

import "./hip-206/IHederaTokenService.sol";

/**
* A contract to assert network behavior with a 1 TPS consensus throttle on NFT mints; 
* in particular, to verify that throttle capacity used during a dispatch that is later
* reverted does not cause further dispatches to be throttled.
*/
contract ConsensusMintCheck {
    address constant HTS_ENTRY_POINT = address(0x167);

    function mintInnerAndOuter(address token, bool revertInner, bytes[] memory innerMeta, bytes[] memory outerMeta) external {
        // Do the inner mint
        try this.mintAndMaybeRevert(token, revertInner, innerMeta) {
          // No-op
        } catch Error(string memory) {
          // No-op
        } catch (bytes memory) {
          // No-op
        }

        // Try our own mint after the inner
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.mintToken.selector, token, 0, outerMeta));
        require(success);
        (int rc, , ) = abi.decode(result, (int32, uint64, int[]));

        if (revertInner) {
          // SUCCESS
          require(rc == 22);
        } else {
          // THROTTLED_AT_CONSENSUS
          require(rc == 366);
        }
    }

    function mintAndMaybeRevert(address token, bool doRevert, bytes[] memory innerMeta) external {
        (bool success, bytes memory result) = HTS_ENTRY_POINT.call(
            abi.encodeWithSelector(
                IHederaTokenService.mintToken.selector, token, 0, innerMeta));
        require(success);
        (int rc, , ) = abi.decode(result, (int32, uint64, int[]));
        require(rc == 22);
        if (doRevert) {
            revert();
        }
    } 
}
