pragma solidity ^0.5.0;

contract MapStorage {
    mapping (uint => uint) public theMap;


    function put(uint key, uint val) public {
        theMap[key] = val;
    }

    function get(uint key) public view returns (uint) {
        return theMap[key];
    }
}