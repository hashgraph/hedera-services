// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.6.0 <0.9.0;
contract Traceability {
    uint256 slot0 = 0;
    uint256 slot1 = 0;
    uint256 slot2 = 0;
    Traceability public sibling;
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

        contractB.delegateCallAddressGetSlot0(sibling2);
        contractB.delegateCallAddressSetSlot0(sibling2, 100);

        contractB.delegateCallAddressGetSlot1(sibling2);
        contractB.delegateCallAddressSetSlot1(sibling2, 0);
    }


    function eetScenario4(address sibling1, address sibling2) external {
        Traceability contractB = Traceability(sibling1);

        this.getSlot0();
        this.setSlot0(3);

        this.getSlot1();
        this.setSlot1(4);

        this.delegateCallAddressGetSlot2(sibling1);

        sibling1.delegatecall(abi.encodeWithSignature("delegateCallAddressSetSlot0(address,uint256)", sibling2, 55));
    }


    function eetScenario6(address sibling1, address sibling2) external {
        Traceability contractB = Traceability(sibling1);

        this.getSlot0();

        this.setSlot1(4);

        this.delegateCallAddressGetSlot2(sibling1);
        this.delegateCallAddressSetSlot2(sibling1, 5);

        sibling1.delegatecall(abi.encodeWithSignature("staticCallAddressGetSlot0(address)", sibling2));
        sibling1.delegatecall(abi.encodeWithSignature("staticCallAddressGetSlot1(address)", sibling2));
    }


    function eetScenario10(address sibling1, address sibling2) external {
        Traceability contractC = Traceability(sibling2);

        this.getSlot0();

        this.setSlot1(4);

        this.callAddressGetSlot2(sibling1);
        this.callAddressSetSlot2(sibling1, 5);

        try contractC.failingGettingAndSetting() {
            return;
        } catch Error(string memory) {
            return;
        } catch (bytes memory) {
            return;
        }
    }


    function eetScenario11(address sibling1, address sibling2) external {
        Traceability contractC = Traceability(sibling2);

        this.getSlot0();

        this.setSlot1(4);

        sibling1.delegatecall("readAndWriteThenRevert()");

        contractC.getSlot0();
        contractC.setSlot0(123);

        contractC.getSlot1();
        contractC.setSlot1(0);
    }


    function eetScenatio3() external {
        this.getSlot0();
        this.setSlot1(55252);
        this.delegateCallSiblingGetSlot2();
        this.delegateCallSiblingSetSlot2(524);
        address(sibling).delegatecall(abi.encodeWithSignature("callAddressGetSlot0(address)", sibling.getSiblingAddress()));
        address(sibling).delegatecall(abi.encodeWithSignature("callAddressSetSlot0(address,uint256)", sibling.getSiblingAddress(), 54));
        address(sibling).delegatecall(abi.encodeWithSignature("callAddressGetSlot1(address)", sibling.getSiblingAddress()));
        address(sibling).delegatecall(abi.encodeWithSignature("callAddressSetSlot1(address,uint256)", sibling.getSiblingAddress(), 0));
    }


    function eetScenatio5() external {
        this.getSlot0();
        this.setSlot1(55252);
        this.callSiblingGetSlot2();
        this.callSiblingSetSlot2(524);
        address(sibling).call(abi.encodeWithSignature("staticCallAddressGetSlot0(address)", sibling.getSiblingAddress()));
        address(sibling).call(abi.encodeWithSignature("staticCallAddressGetSlot1(address)", sibling.getSiblingAddress()));
    }


    function eetScenatio7() external {
        // TODO: callcode is deprecated, will probably need separate contract, compiled with an older version in order to test this
        // this.getSlot0();
        // this.setSlot1(55252);
        // this.callSiblingGetSlot2();
        // this.callSiblingSetSlot2(524);
        // address(sibling).call(abi.encodeWithSignature("callcodeAddressGetSlot0(address)", sibling.getSiblingAddress()));
        // address(sibling).call(abi.encodeWithSignature("callcodeAddressSetSlot0(address,uint256)", sibling.getSiblingAddress(), 54));
        // address(sibling).call(abi.encodeWithSignature("callcodeAddressGetSlot1(address)", sibling.getSiblingAddress()));
        // address(sibling).call(abi.encodeWithSignature("callcodeAddressSetSlot1(address,uint256)", sibling.getSiblingAddress(), 0));
    }


    function eetScenatio9() external {
        this.getSlot0();
        this.setSlot1(55252);
        this.callSiblingGetSlot2();
        this.callSiblingSetSlot2(524);
        Traceability contractC = Traceability(sibling.getSiblingAddress());
        contractC.callToContractCForE2EScenario92();
    }

    function failingGettingAndSetting() external {
        this.getSlot0();
        this.setSlot0(12);
        this.getSlot1();
        this.setSlot1(0);
        revert();
    }

    function readAndWriteThenRevert() external {
        this.getSlot2();
        this.setSlot2(10);
        revert();
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
    function delegateCallSiblingSetSlot0(uint256 slot) external {
        address(sibling).delegatecall(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }
    function delegateCallSiblingGetSlot0() external {
        address(sibling).delegatecall(abi.encodeWithSignature("getSlot0()"));
    }
    function delegateCallSiblingSetSlot1(uint256 slot) external {
        address(sibling).delegatecall(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }
    function delegateCallSiblingGetSlot1() external {
        address(sibling).delegatecall(abi.encodeWithSignature("getSlot1()"));
    }
    function delegateCallSiblingSetSlot2(uint256 slot) external {
        address(sibling).delegatecall(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }
    function delegateCallSiblingGetSlot2() external {
        address(sibling).delegatecall(abi.encodeWithSignature("getSlot2()"));
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


    function delegateCallAddressGetSlot0(address _address) external {
        _address.delegatecall(abi.encodeWithSignature("getSlot0()"));
    }
    function delegateCallAddressSetSlot0(address _address, uint256 slot) external {
        _address.delegatecall(abi.encodeWithSignature("setSlot0(uint256)", slot));
    }


    function delegateCallAddressGetSlot1(address _address) external {
        _address.delegatecall(abi.encodeWithSignature("getSlot1()"));
    }
    function delegateCallAddressSetSlot1(address _address, uint256 slot) external {
        _address.delegatecall(abi.encodeWithSignature("setSlot1(uint256)", slot));
    }


    function delegateCallAddressGetSlot2(address _address) external {
        _address.delegatecall(abi.encodeWithSignature("getSlot2()"));
    }
    function delegateCallAddressSetSlot2(address _address, uint256 slot) external {
        _address.delegatecall(abi.encodeWithSignature("setSlot2(uint256)", slot));
    }


    function staticCallAddressGetSlot0(address _address) external {
        _address.staticcall(abi.encodeWithSignature("getSlot0()"));
    }
    function staticCallAddressGetSlot1(address _address) external {
        _address.staticcall(abi.encodeWithSignature("getSlot1()"));
    }
    // function callcodeAddressGetSlot0(address _address) external {
    //     _address.callcode(abi.encodeWithSignature("getSlot0()"));
    // }
    // function callcodeAddressSetSlot0(address _address, uint256 slot) external{
    //     _address.callcode(abi.encodeWithSignature("setSlot0(uint256)", slot));
    // }
    // function callcodeAddressGetSlot1(address _address) external {
    //     _address.callcode(abi.encodeWithSignature("getSlot1()"));
    // }
    // function callcodeAddressSetSlot1(address _address, uint256 slot) external {
    //     _address.callcode(abi.encodeWithSignature("setSlot1(uint256)", slot));
    // }
    // function callToContractCForE2EScenario9(address _address) external {
    //     _address.call(abi.encodeWithSignature("getSlot0()"));
    //     _address.call(abi.encodeWithSignature("setSlot0(uint256)", 55));
    //     _address.call(abi.encodeWithSignature("getSlot1()"));
    //     _address.call(abi.encodeWithSignature("setSlot1(uint256)", 155));
    //     revert();
    // }
    function callToContractCForE2EScenario92() external {
        this.getSlot0();
        this.setSlot0(55);
        this.getSlot1();
        this.setSlot1(155);
        revert();
    }
}