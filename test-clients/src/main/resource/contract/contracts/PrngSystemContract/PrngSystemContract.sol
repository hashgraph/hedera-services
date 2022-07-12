// SPDX-License-Identifier: Apache-2.0

import "./IPrngSystemContract.sol";

contract PrngSystemContract {
    address constant PRECOMPILE_ADDRESS = address(0x169);

    function getPseudorandomSeed() external returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IPrngSystemContract.getPseudorandomSeed.selector));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function getPseudorandomNumber(uint32 range) external returns (uint32 randomNum) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IPrngSystemContract.getPseudorandomNumber.selector, range));
        require(success);
        randomNum = abi.decode(result, (uint32));
    }
}
