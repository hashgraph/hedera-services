// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

contract PrngSeedOperationContract {

    function getPseudorandomSeed() external view returns (bytes32 randomBytes) {
        randomBytes = (bytes32) (block.difficulty);
    }
}
