// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IRandomGenerate {
    function random256BitGenerator() external returns (bytes32);

    function randomNumberGeneratorInRange(uint32 range) external returns (uint32);
}
