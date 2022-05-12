pragma solidity ^0.5.0;
contract SizedArray {
    uint256[] sizedArray;
    function setSizeInKB (uint256 _howManyKB) public {
        sizedArray.length = _howManyKB * 16;
        // Initialize all locations because zeros are not actually persisted.
        for (uint i=0; i <_howManyKB * 16; i++) {
            sizedArray[i] = 17;
        }
    }

    function getLength() public view returns (uint _get) {
        return sizedArray.length;
    }

}
contract TwoBigArrays {
    uint256[] bigArray;
    SizedArray mySizedArray;
    constructor() public {
        mySizedArray =  new SizedArray();
    }
    // Functions for array passing and storage
    function setSizesInKB (uint256 _howManyKB,uint256 _secondContractArrayKB) public {
        bigArray.length = _howManyKB * 16;
        // Initialize all locations because zeros are not actually persisted.
        for (uint i=0; i <_howManyKB * 16; i++) {
            bigArray[i] = 17;
        }
        mySizedArray.setSizeInKB(_secondContractArrayKB);
    }

    function secondContractArraySize() public view returns (uint _get) {
        return mySizedArray.getLength();
    }

    function firstContractArraySize() public view returns (uint _get) {
        return bigArray.length;
    }

    function changeArray(uint256 _value) public {
        bigArray[1] = _value;
    }
}

