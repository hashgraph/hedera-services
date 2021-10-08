pragma solidity ^0.5.0;

// Contract to grow storage size gradually, since using storage size up to the limit in one go will
// exceed the overall gas limit.
contract GrowArray {
    uint256[] public bigArray;

    // Grow from current size to _limit
    function growTo (uint256 _targetSize) public {
        uint currSize = bigArray.length;
        if (_targetSize <= currSize) {
            revert();
        }
        bigArray.length = _targetSize;
        // Initialize all new locations because zeros are not actually persisted
        for (uint i=currSize; i < _targetSize; i++) {
            bigArray[i] = 1;
        }
    }

    function changeArray(uint256 _value) public {
        bigArray[1] = _value;
    }
}