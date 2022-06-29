// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IRandomGenerate {
    // Generates a 256-bit pseudorandom number using the first 256-bits of running hash of n-3 transaction record.
    // When n-3 running hash of transaction record is not present, doesn't return the 256 bit pseudorandom number.
    function random256BitGenerator() external returns (bytes32);

    // Given an unsigned 32-bit integer "range", generates a pseudorandom number within that range.
    // Uses the first 32-bits of running hash of n-3 transaction record to generate the pseudorandom number.
    // When running hash of n-3 transaction record is not present or invalid, doesn't return the pseudorandom number.
    function randomNumberGeneratorInRange(uint32 range) external returns (uint32);
}
