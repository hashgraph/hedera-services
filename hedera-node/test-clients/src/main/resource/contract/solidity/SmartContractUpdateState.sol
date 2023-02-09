// SPDX-License-Identifier: GPL-3.0
//pragma solidity ^0.8.0;
//pragma solidity ^0.5.9;
pragma solidity <=0.8.10;


contract SmartContractUpdateState {

    uint256 public     counter;

    uint256 public     sum;

    bytes32            bMesg;
    bytes32            bArry;

    //bytes32[] public   keys;
    //mapping(bytes32 => bytes32)   public hashMap;

    uint256[] public   arrs;

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

    //function get_key(uint32 i) public view returns (bytes32) {
    //    return keys[i];
    //}

    //function get_nkey() public view returns (uint) {
    //    return keys.length;
    //}

    //function get_map(bytes32 _key) public view returns (bytes32) {
    //    return hashMap[_key];
    //}

    //function create_map(uint32 n) public {
    //    for (uint256 i = 0; i < n; i++) {
    //        bytes32 k = keccak256(abi.encodePacked(bytes32(i + keys.length), block.number));
    //        hashMap[k] = k;
    //        counter++;
    //        keys.push(k);
    //        counter++;
    //    }
    //}

    // Updates the arrs object with noise, targeting the targetSize in 256 bit words
    // If the arrs length is less than the target size the array is grown 
    // by one value and the noise is appended, no other actions are taken.
    // If the arrs length is greater than the target size it is shrunk by one 
    // by removing the last item.
    // If the array is not grown then the value at the index that is the modulus 
    // of the length and the noise value is set to the noise value
    //
    // @param targetSize The target length of the array
    // @param noise Arbitrary data (preferably random or pseudorandom) to be 
    //              stored in or at the end of the arrs array.
    //
    function get_arrs_len() public view returns (uint) {
        uint len = arrs.length;
        return len;
    }

    function get_arrs_len256() public view returns (uint256) {
        uint256 len256 = arrs.length;
        return len256;
    }

    function update_arrs(uint32 targetSize, uint256 noise) external {
        uint256 len = arrs.length;
        if (len < targetSize) {
            arrs.push(noise);
        } else {
            if (len > targetSize) {
                arrs.pop();
            }
            arrs[noise % len] = noise;
        }
    }

    function update_arrs_n(uint32 n, uint32 targetSize, uint256 noise) external {
        uint32 num = n;
        uint32 size = targetSize;
        if (n == 0) {
            num = targetSize % 256 + 1;
        }
        for (uint32 i = 0; i < num; i++) {
            
            if (n == 0) {
                size = (i * (size + targetSize)) % 256; 
            }
            this.update_arrs(size, noise);
        }
    }
}

