// SPDX-License-Identifier: GPL-3.0
pragma solidity ^0.8.9;

contract AddressValueRet {
    function createReturner(bytes32 salt) public {
        address predictedAddress = address(uint160(uint(keccak256(abi.encodePacked(
                bytes1(0xff),
                address(this),
                salt,
                keccak256(abi.encodePacked(
                    type(Returner).creationCode
                ))
            )))));

        Returner returner = new Returner{salt: salt}();
        require(address(returner) == predictedAddress);
    }

    function callReturner(address returner_address) public view returns (
        uint160
    ) {
        Returner q = Returner(returner_address);
        return q.returnThis();
    }
}

contract Returner {
    Placeholder placeholder;

    function returnThis() public view returns (uint160) {
        return uint160(address(this));
    }

    function createPlaceholder() public {
        placeholder = new Placeholder();
    }
}

contract Placeholder {
}
