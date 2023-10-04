// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract HollowAccountCreator {
    
    // 0x13848c3e38f8886f3f5d2ad9dff80d8092c2bbb8efd5b887a99c2c6cfc09ac2a
    event Response(bool indexed success, bytes data);

    function testCallFoo(address payable _addr, uint gasLimit) public payable {
        
        (bool success, bytes memory data) = _addr.call{value: msg.value, gas: gasLimit}(
            abi.encodeWithSignature('foo(string,uint256)', 'call foo', 123)
        );

        emit Response(success, data);
    }
}
