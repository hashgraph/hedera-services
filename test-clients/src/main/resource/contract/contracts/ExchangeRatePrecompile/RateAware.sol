// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.5.0 <0.9.0;

abstract contract RateAware {
    uint64 constant TINY_PARTS_PER_WHOLE = uint64(100_000_000);
    bytes1 constant TO_TINYBARS = bytes1(0xbb);
    bytes1 constant TO_TINYCENTS = bytes1(0xcc);
    address constant precompileAddress = address(0x21D4);

    function toTinybars(int64 tinycents) 
        internal 
        returns (bool success, int64 tinybars) 
    {
        bytes memory result;
        (success, result) = precompileAddress.call(
            abi.encodePacked(TO_TINYBARS, tinycents));
        tinybars = success ? abi.decode(result, (int64)) : int64(0);
    }

    function toTinycents(int64 tinybars) 
        internal 
        returns (bool success, int64 tinycents) 
    {
        bytes memory result;
        (success, result) = precompileAddress.call(
            abi.encodePacked(TO_TINYCENTS, tinybars));
        tinycents = success ? abi.decode(result, (int64)) : int64(0);
    }

    modifier costsCents(uint64 cents) {
        uint64 tinycents = cents * TINY_PARTS_PER_WHOLE;
        (bool success, int64 tinybar) = toTinybars(int64(tinycents));
        require(success);
        require(uint64(msg.value) >= uint64(tinybar));
        _;
    } 
}
