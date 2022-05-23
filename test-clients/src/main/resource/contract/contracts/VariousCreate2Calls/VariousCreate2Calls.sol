// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract VariousCreate2Calls {
    bytes32 constant salt = 0x0101010101010100101010101010100101010101010101110101010101010101;

    Caller normal;
    Caller eip1014;

    constructor() {
        eip1014 = new Caller{salt: salt}();
        normal = new Caller();
    }

    function makeNormalCall() public view returns (address) {
        return eip1014.getThisAddress();
    }

    function makeStaticCall() public view returns (address) {
        bytes memory select = abi.encodeWithSelector(Caller.getThisAddress.selector);
        (, bytes memory result) = address(eip1014).staticcall(select);
        return abi.decode(result, (address));
    }

    function makeDelegateCall() public returns (address) {
        bytes memory select = abi.encodeWithSelector(Caller.getThisAddress.selector);
        return eip1014.delegate(normal, select);
    }

    function makeCallCode() public returns (address) {
        bytes memory select = abi.encodeWithSelector(Caller.getThisAddress.selector);
        return eip1014.code(address(normal), select);
    }
}

contract Caller {
    function getThisAddress() public view returns (address) {
        return address(this);
    }

    function delegate(Caller to, bytes memory select) public returns (address) {
        (, bytes memory result) = address(to).delegatecall(select);
        return abi.decode(result, (address));
    }

    function code(address to, bytes memory select) public returns (address answer) {
        bool success;

        assembly {
            let output := mload(0x40)
            success := callcode(
            gas(),
            to,
            0,
            add(select, 32),
            mload(select),
            output,
            0xA0)
            answer := mload(output)
        }
    }
}
