// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IPrngSystemContract {
    // Generates a 256-bit pseudorandom number using the first 256-bits of running hash of n-3 transaction record.
    // When n-3 running hash of transaction record is not present, doesn't return the 256 bit pseudorandom number.
    function getPseudorandomSeed() external returns (bytes32);

    // Given an unsigned 32-bit integer "range" and a 256-bit "seed", generates a pseudorandom number within that range.
    // Since it uses a seed value to generate pseudorandom number, running the contract multiple times with same seed
    // will generate same  pseudorandom number.
    function getPseudorandomNumber(uint32 range, uint256 seed) external returns (uint32);
}
