// SPDX-License-Identifier: Apache-2.0

import "./IRandomGenerate.sol";

contract RandomGeneratePrecompile {
    address constant PRECOMPILE_ADDRESS = address(0x169);

    function random256BitGenerator() external returns (bytes32 randomBytes) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IRandomGenerate.random256BitGenerator.selector));
        require(success);
        randomBytes = abi.decode(result, (bytes32));
    }

    function randomNumberGeneratorInRange(uint32 range) external returns (uint32 randomNum) {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.call(
            abi.encodeWithSelector(IRandomGenerate.randomNumberGeneratorInRange.selector, range));
        require(success);
        randomNum = abi.decode(result, (uint32));
    }
}
