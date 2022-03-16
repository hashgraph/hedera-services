pragma solidity ^0.8.12;

contract AliasedOperator {
    address public token;

    function initialize(address _token) public {
        token = _token;
    }

    function transfer(address to, uint value) external {
        (bool success, bytes memory data) = token.call(abi.encodeWithSelector(bytes4(keccak256(bytes('transfer(address,uint256)'))), to, value));
        require(success && (data.length == 0 || abi.decode(data, (bool))));
    }

    function associate() external {
        address(0x167).call(abi.encodeWithSignature("associateToken(address,address)", address(this), token));
    }
}
