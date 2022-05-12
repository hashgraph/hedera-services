pragma solidity ^0.4.24;

contract PrecompiledOpcodes {

    function runAddmod() public pure returns (uint _resp) {
        assembly {
            _resp := addmod(8, 9, 10)
        }
    }

    function runMulmod() public pure returns (uint _resp) {
        assembly {
            _resp := mulmod(8, 9, 10)
        }
    }

    function runKeccak256 (string memory _value) public pure returns (bytes32 _resp) {
        _resp = keccak256(abi.encodePacked(_value));
    }

    function runRipemd160(string memory _value) public pure returns (bytes20 _resp) {
        _resp = ripemd160(abi.encodePacked(_value));
    }

    function runSha256(string memory _value) public pure returns (bytes32 _resp) {
        _resp = sha256(abi.encodePacked(_value));
    }

    function runSha3(string memory _value) public pure returns (bytes32 _resp) {
        _resp = sha3(abi.encodePacked(_value));
    }
}
