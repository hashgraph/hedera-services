pragma solidity ^0.5.0;
contract LargeStorage {
    struct SpaceEater {
        uint256 index;
        uint256[] passedArray;
    }
    mapping (uint256 => SpaceEater) spaceEaters;
    mapping (uint256 => uint256) spaceSavers;

    // Functions for array passing and storage
    function storeArray(uint256 _index, uint256[] memory _array) public {
        spaceEaters[_index]  = SpaceEater({
            index: _index,
            passedArray: _array
        });
    }

    // Minimize storage due to gas limitation
    function wasteArray(uint256 _index, uint256[] memory _array) public {
        spaceSavers[_index] = _array[0];
    }

    function getArray(uint256 _index) public view returns (uint256[] memory _array) {
        _array = spaceEaters[_index].passedArray;
    }
}