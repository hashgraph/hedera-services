// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.0 <=0.5.0;

contract TraceabilityCallcode {
    uint256 slot0 = 0;
    uint256 slot1 = 0;
    uint256 slot2 = 0;
    Traceability public sibling;
    constructor(uint256 _slot0, uint256 _slot1, uint256 _slot2) public {
        slot0 = _slot0;
        slot1 = _slot1;
        slot2 = _slot2;
    }
    // POW case
    function eetScenatio0() external {
        this.getSlot0();
        this.setSlot1(1);
        this.callSiblingGetSlot2();
        address(sibling).call(abi.encodeWithSignature("callSiblingGetSlot0()"));
    }


    function eetScenatio1() external {
        this.getSlot0();
        this.setSlot1(55);
        this.callSiblingGetSlot2();
        this.callSiblingSetSlot2(143);
        address(sibling).call(abi.encodeWithSignature("callSiblingGetSlot0()"));
        address(sibling).call(abi.encodeWithSignature("callSiblingSetSlot0(uint256)", 0));
        address(sibling).call(abi.encodeWithSignature("callSiblingGetSlot1()"));
        address(sibling).call(abi.encodeWithSignature("callSiblingSetSlot1(uint256)", 0));
    }


    function eetScenario2(address sibling1, address sibling2) external {
        Traceability contractB = Traceability(sibling1);

        this.getSlot0();

        this.setSlot1(55);

        this.callAddressGetSlot2(sibling1);
        this.callAddressSetSlot2(sibling1, 143);

        // contractB.delegateCallAddressGetSlot0(sibling2);
        // contractB.delegateCallAddressSetSlot0(sibling2, 100);

        // contractB.delegateCallAddressGetSlot1(sibling2);
        // contractB.delegateCallAddressSetSlot1(sibling2, 0);
    }


    // Contract A executes a CALL to Contract B, which in turn executes a CALLCODE to Contract C.
    // Fields in all contracts are accessed / written to, but in ContractCallResult's stateChanges there will be stateChange only for Contract A and Contract B

    // Execution and results should be the same as CALL B → DELEGATE CALL C.


    // Slot 0 in Contract A is read as 0, but not written to.
    // Slot 1 in Contract A is written to, without first being read.
    // Slot 2 in Contract B is read as ≠ 0 and written to ≠ 0.
    // Slot 0 from Contract C is read as 0 and written as ≠ 0.
    // Slot 1 from Contract C is read as ≠ 0 as written as = 0.

    // Expected stateChanges:  2 different stateChanges  → 1 for Contract A, 1 for contract B (Contract C is delegate called)
    // Contract A:
    //   slot 0 → A contract that only reads a zero value from slot zero will have an empty message.
    //   slot 1 → read and written
    //   slot 2 → absent.
    // Contract B:
    //   slot 0 → read and written
    //   slot 1 → read and written
    //   slot 2 → read and written
    // Contract C (no state changes):
    //   slot 0 → absent
    //   slot 1 → absent
    //   slot 2 → absent

    function eetScenatio7() external {
        this.getSlot0();
        this.setSlot1(55252);

        this.callSiblingGetSlot2();
        this.callSiblingSetSlot2(524);

        address(sibling).call(abi.encodeWithSignature("callcodeAddressGetSlot0(address)", sibling.getSiblingAddress()));
        address(sibling).call(abi.encodeWithSignature("callcodeAddressSetSlot0(address,uint256)", sibling.getSiblingAddress(), 54));
    }


    // Contract A executes a CALLCODE to Contract B, which in turn executes a CALLCODE to Contract C.
    // There are state changes in all three contracts, but in ContractCallResult's stateChanges there will be stateChanges only for Contract A.

    // Execution and results should be the same as DELEGATE CALL B → DELEGATE CALL C

    // Slot 0 in Contract A is read and written to.
    // Slot 1 in Contract A is read and written to.
    // Slot 2 in Contract B is read, but not written to.
    // Slot 0 value from Contract C is written as 55, without being read

    // Expected stateChanges: 1 for Contract A (Contract B is delegate called, Contract C is delegate called)
    // Contract A:
    //   slot 0 → read and written
    //   slot 1 → read and written
    //   slot 2 → read and written-
    // Contract B (no state change):
    //   slot 0 → absent
    //   slot 1 → absent
    //   slot 2 → absent
    // Contract C (no state change):
    //   slot 0 → absent
    //   slot 1 → absent
    //   slot 2 → absent

    function eetScenatio8() external {
        this.getSlot0();
        this.setSlot0(2);

        this.getSlot1();
        this.setSlot1(55252);

        this.callcodeSiblingGetSlot2();
        this.callcodeSiblingSetSlot2(524);

        address(sibling).callcode(abi.encodeWithSignature("callcodeAddressSetSlot0(address,uint256)", sibling.getSiblingAddress(), 55));
    }



    function setSibling(address _sibling) external {
        sibling = Traceability(_sibling);
    }


    function getSiblingAddress() external returns(address) {
        return address(sibling);
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
        address(sibling).call(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }
    function callSiblingGetSlot0() external {
        address(sibling).call(abi.encodeWithSignature("getSlot0()"));
    }
    function callSiblingSetSlot1(uint256 slot) external {
        address(sibling).call(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }
    function callSiblingGetSlot1() external {
        address(sibling).call(abi.encodeWithSignature("getSlot1()"));
    }
    function callSiblingSetSlot2(uint256 slot) external {
        address(sibling).call(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }
    function callSiblingGetSlot2() external {
        address(sibling).call(abi.encodeWithSignature("getSlot2()"));

    }
    function callcodeSiblingSetSlot2(uint256 slot) external {
        address(sibling).callcode(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }
    function callcodeSiblingGetSlot2() external {
        address(sibling).callcode(abi.encodeWithSignature("getSlot2()"));

    }
    function callAddressGetSlot0(address _address) external {
        _address.call(abi.encodeWithSignature("getSlot0()"));
    }
    function callAddressSetSlot0(address _address, uint256 slot) external {
        _address.call(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }


    function callAddressGetSlot1(address _address) external {
        _address.call(abi.encodeWithSignature("getSlot1()"));
    }
    function callAddressSetSlot1(address _address, uint256 slot) external {
        _address.call(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }

    function callAddressGetSlot2(address _address) external {
        _address.call(abi.encodeWithSignature("getSlot2()"));
    }
    function callAddressSetSlot2(address _address, uint256 slot) external {
        _address.call(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }

    function callcodeAddressGetSlot0(address _address) external {
        _address.callcode(abi.encodeWithSignature("getSlot0()"));
    }
    function callcodeAddressSetSlot0(address _address, uint256 slot) external{
        _address.callcode(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }
    function callcodeAddressGetSlot1(address _address) external {
        _address.callcode(abi.encodeWithSignature("getSlot1()"));
    }
    function callcodeAddressSetSlot1(address _address, uint256 slot) external {
        _address.callcode(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }
}
