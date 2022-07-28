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
}
