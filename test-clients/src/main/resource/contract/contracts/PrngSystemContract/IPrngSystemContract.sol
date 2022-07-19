// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IPrngSystemContract {
    // Generates a 256-bit pseudorandom seed using the first 256-bits of running hash of n-3 transaction record.
    function getPseudorandomSeed() external returns (bytes32);

    // Given an unsigned 32-bit integer "range", generates a pseudorandom number X within 0 <= X < range.
    // Uses the first 32-bits of running hash of n-3 transaction record to generate the pseudorandom number.
    // When the range provided is not greater than zero or not in the integer range, contract fails with
    // INVALID_PRNG_RANGE.
    function getPseudorandomNumber(uint32 range) external returns (uint32);
}
