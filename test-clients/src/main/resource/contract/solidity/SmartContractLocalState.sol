// SPDX-License-Identifier: GPL-3.0
//pragma solidity ^0.8.0;
//pragma solidity ^0.5.9;
pragma solidity <=0.8.10;


contract SmartContractLocalState {

    uint32 private       num;

    constructor() {
        num = 162;
    }

    function local_sum(uint32 n) public pure returns (uint32) {
        uint32 i;
        for (i = 0; i < n; i++) {
        }
        return i;
    }

    function local_mem(uint32 n) public pure returns (uint32) {
        uint32 i;
        for (i = 0; i < n; i++) {
            string memory dummy = new string(256);
            dummy = "A";
        }
        return i;
    }

    function get_num() public view returns (uint32) {
        return num;
    }

    function get_num_n(uint32 n) public view returns (uint32) {
        uint32 i;
        for (i = 0; i < n; i++) {
        }
        return num;
    }

    function set_num(uint32 n) public {
        num = n;
    }

    function set_num_n(uint32 n) public {
        uint32 i;
        for (i = 0; i < n; i++) {
            num = i;
        }
    }


}

