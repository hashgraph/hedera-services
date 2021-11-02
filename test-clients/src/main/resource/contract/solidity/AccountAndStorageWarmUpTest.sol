// SPDX-License-Identifier: CC-BY-SA-4.0
pragma solidity 0.6.4;

contract Base {

    uint public dataA;
    uint public dataB;

    function setAB(uint a, uint b) public {
        dataA = a;
        dataB = b;
    }

    function getA() public view returns(uint) {
        return dataA;
    }

    function getB() public view returns(uint) {
        return dataB;
    }

}

contract Extra {

    Base base;
    uint public extraA;

    constructor(address baseAddress) public {
        base = Base(baseAddress);
        extraA = base.getA() + base.getB();
    }

    function getBaseAddress() public view returns(address) {
        return address(base);
    }

    function baseGetA() public view returns(uint) {
        return base.getA();
    }

    function baseGetB() public view returns(uint) {
        return base.getB();
    }

    function baseSetAB(uint a, uint b) public returns(bool success) {
        base.setAB(a,b);
        return true;
    }

    function getExtraA() public view returns(uint) {
        return extraA;
    }

    function getSumOfBaseAAndBaseB() public view returns(uint) {
        return base.getA() + base.getB();
    }
}