// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

contract Traceability {
    uint256 slot0 = 0;
    uint256 slot1 = 0;
    uint256 slot2 = 0;

    address public sibling = address(0);

    constructor(uint256 _slot0, uint256 _slot1, uint256 _slot2) {
        slot0 = _slot0;
        slot1 = _slot1;
        slot2 = _slot2;
    }

    // POW case
    function eetScenatio0() external {
        this.getSlot0();
        this.setSlot1(1);
        this.callSiblingGetSlot2();
        sibling.call(abi.encodeWithSignature("callSiblingGetSlot0()"));
    }

    function eetScenatio1() external {
        this.getSlot0();
        this.setSlot1(55);
        this.callSiblingGetSlot2();
        this.callSiblingSetSlot2(143);
        sibling.call(abi.encodeWithSignature("callSiblingGetSlot0()"));
        sibling.call(abi.encodeWithSignature("callSiblingSetSlot0(0)"));
        sibling.call(abi.encodeWithSignature("callSiblingGetSlot1()"));
        sibling.call(abi.encodeWithSignature("callSiblingSetSlot1(0)"));
    }

    function setSibling(address _sibling) external {
        sibling = _sibling;
    }

    function getSlot0() external returns(uint256) {
        return slot0;
    }

    function setSlot0(uint256 _slot0) external {
        slot0 = _slot0;
    }

    function getSlot1() external returns(uint256) {
        return slot1;
    }

    function setSlot1(uint256 _slot1) external {
        slot1 = _slot1;
    }

    function getSlot2() external returns(uint256) {
        return slot2;
    }

    function setSlot2(uint256 _slot2) external {
        slot2 = _slot2;
    }

    function callSiblingSetSlot0(uint256 slot) external {
        sibling.call(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }

    function callSiblingGetSlot0() external {
        sibling.call(abi.encodeWithSignature("getSlot0()"));
    }

    function callSiblingSetSlot1(uint256 slot) external {
        sibling.call(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }

    function callSiblingGetSlot1() external {
        sibling.call(abi.encodeWithSignature("getSlot1()"));
    }

    function callSiblingSetSlot2(uint256 slot) external {
        sibling.call(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }

    function callSiblingGetSlot2() external {
        sibling.call(abi.encodeWithSignature("getSlot2()"));
    }

    function delegateCallSiblingSetSlot0(uint256 slot) external {
        sibling.delegatecall(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }

    function delegateCallSiblingGetSlot0() external {
        sibling.delegatecall(abi.encodeWithSignature("getSlot0()"));
    }

    function delegateCallSiblingSetSlot1(uint256 slot) external {
        sibling.delegatecall(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }

    function delegateCallSiblingGetSlot1() external {
        sibling.delegatecall(abi.encodeWithSignature("getSlot1()"));
    }

    function delegateCallSiblingSetSlot2(uint256 slot) external {
        sibling.delegatecall(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }

    function delegateCallSiblingGetSlot2() external {
        sibling.delegatecall(abi.encodeWithSignature("getSlot2()"));
    }
}