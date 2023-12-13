pragma solidity ^0.5.0;
contract Loops {
    uint256 pickle = 1;

    function forever() public pure {
        while (true) {

        }
    }

    function limited (uint64 _limit) public pure {
        for (uint64 idx = 0; idx <= _limit; idx++) {

        }
    }
}