// SPDX-License-Identifier: GPL-3.0
//pragma solidity ^0.8.0;
//pragma solidity ^0.5.9;
pragma solidity <=0.8.16;


contract SmartContractState {

    uint256 public     counter;

    uint256 public     sum;

    bytes32            bMesg;
    bytes32            bArry;

    bytes32[] public   keys;
    mapping(bytes32 => bytes32)   public hashMap;

//    bytes32[] public   arrs;
//    mapping(bytes32 => uint256[]) public arryMap;


    function get_counter() public view returns (uint256) {
        return counter;
    }

    function get_sum() public view returns (uint256) {
        return sum;
    }

    function set_sum(uint32 n) public {
        for (uint32 i = 0; i < n; i++) {
            sum += i;
        }
    }

    function get_bytes() public view returns (bytes32) {
        return bMesg;
    }

    function get_bytes_n(uint32 n) public view returns (bytes32) {
        bytes32 m;
        for (uint32 i = 0; i < n; i++) {
            m = bMesg;
        }
        return m;
    }

    function set_bytes(bytes32 _b) public {
        bMesg = _b;
    }

    function set_bytes_n(uint32 n) public {
        for (uint32 i = 0; i < n; i++) {
            bArry = bMesg;
        }
    }

    function get_key(uint32 i) public view returns (bytes32) {
        return keys[i];
    }

    function get_nkey() public view returns (uint) {
        return keys.length;
    }

    function get_map(bytes32 _key) public view returns (bytes32) {
        return hashMap[_key];
    }

    function create_map(uint32 n) public {
        for (uint256 i = 0; i < n; i++) {
            bytes32 k = keccak256(abi.encodePacked(bytes32(i + keys.length), block.number));
            hashMap[k] = k;
            counter++;
            keys.push(k);
            counter++;
        }
    }

}
