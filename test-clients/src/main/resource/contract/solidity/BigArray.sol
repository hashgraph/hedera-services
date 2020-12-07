pragma solidity ^0.5.0;
contract BigArray {
    uint256[] public bigArray;

    // Functions for array passing and storage
    function setSizeInKB (uint256 _howManyKB) public {
        bigArray.length = _howManyKB * 16;
        // Initialize all locations because zeros are not actually persisted.
        for (uint i=0; i <_howManyKB * 16; i++) {
            bigArray[i] = 17;
        }
    }

    function changeArray(uint256 _value) public {
        bigArray[1] = _value;
    }
}