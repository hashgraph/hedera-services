// SPDX-License-Identifier: Apache-2.0
pragma solidity 0.8.15;

contract SimpleStorage {
    uint32 storedLongData = 15;
    uint8 storedIntegerData = 15;
    uint storedData = 15;
    address storedAddress = 0x0000000000000000000000000000000000000000;

    function setAddress(address x) public {
        storedAddress = x;
    }

    function getAddress() public view returns (address _get) {
        return storedAddress;
    }

    function setInteger(uint8 x) public {
        storedIntegerData = x;
    }

    function getInteger() public view returns (uint8 _get) {
        return storedIntegerData;
    }

    function setLong(uint32 x) public {
        storedLongData = x;
    }

    function getLong() public view returns (uint32 _get) {
        return storedLongData;
    }

    function set(uint x) public {
        storedData = x;
    }

    function get() public view returns (uint _get) {
        return storedData;
    }
}