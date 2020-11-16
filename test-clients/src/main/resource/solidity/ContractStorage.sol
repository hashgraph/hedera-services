pragma solidity ^0.5.0;
contract ContractStorage {
    uint256[] public bigArray;

    // set the size of the array
    function setSizeInKB (uint256 _howManyKB) public {
        bigArray.length = _howManyKB * 16;
        // Initialize all locations because zeros are not actually persisted.
        for (uint i=0; i < _howManyKB * 16; i++) {
            bigArray[i] = 17;
        }
    }

	// change the values in the array from 0 - _size
    function changeArray(uint256 _value, uint _size) public {
    	// set all locations from 0 -> _size to _value
    	for (uint i=0; i < _size * 16; i++) {
        	bigArray[i] = _value;
        }
    }

	// get the array from 0 - _index
    function getData(uint _index) public view returns (uint[] memory) {
		uint256[] memory tempArray = new uint256[](_index * 16);
		for (uint i=0; i < tempArray.length; i++) {
			tempArray[i] = bigArray[i];
		}
        return tempArray;
    }
}