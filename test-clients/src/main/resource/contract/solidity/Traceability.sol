// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;

contract Traceability {
    uint256 slot1 = 0;
    uint256 slot2 = 0;
    uint256 slot3 = 0;

    address public sibling = address(0);

    function eetScenatio1() external {
        this.getSlot1();
        this.setSlot2(1);
        this.callSiblingGetSlot3();
        sibling.call(abi.encodeWithSignature("callSiblingGetSlot1()"));
    }

    function setSibling(address _sibling) external {
        sibling = _sibling;
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

    function getSlot3() external returns(uint256) {
        return slot3;
    }

    function setSlot3(uint256 _slot3) external {
        slot3 = _slot3;
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

    function callSiblingSetSlot3(uint256 slot) external {
        sibling.call(abi.encodeWithSignature("setSlot3(uint256)", slot));
    }

    function callSiblingGetSlot3() external {
        sibling.call(abi.encodeWithSignature("getSlot3()"));
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

    function delegateCallSiblingSetSlot3(uint256 slot) external {
        sibling.delegatecall(abi.encodeWithSignature("setSlot3(uint256)", slot));
    }

    function delegateCallSiblingGetSlot3() external {
        sibling.delegatecall(abi.encodeWithSignature("getSlot3()"));
    }
}