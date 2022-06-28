// SPDX-License-Identifier: Apache-2.0

import "./IRandomGenerate.sol";

contract RandomGeneratePrecompile {
    address constant PRECOMPILE_ADDRESS = address(0x169);

    function random256BitGenerator() external returns (uint256 randomNum) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IRandomGenerate.random256BitGenerator.selector));
        require(success);
        randomNum = abi.decode(result, (uint256));
    }

    function randomNumberGeneratorInRange(uint32 range) external returns (uint32 randomNum) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IRandomGenerate.random256BitGenerator.selector, range));
        require(success);
        randomNum = abi.decode(result, (uint32));
    }
}
