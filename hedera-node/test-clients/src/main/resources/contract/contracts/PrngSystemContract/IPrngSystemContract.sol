// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

interface IPrngSystemContract {
    // Generates a 256-bit pseudorandom seed using the first 256-bits of running hash of n-3 transaction record.
    // Users can generate a pseudorandom number in a specified range using the seed by (integer value of seed % range)
    function getPseudorandomSeed() external returns (bytes32);
}
