// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.4.9 <0.9.0;

import "../PrngSystemContract/PrngSystemContract.sol";

contract GracefullyFailingPrng is PrngSystemContract {
    string public constant largeInput = "0x02020202020202002020202020202002020202020202022202020202020202020202020202020200202020202020200202020202020202220202020202020202020202020202020020202020202020020202020202020222020202020202020202020202020202002020202020202002020202020202022202020202020202020202020202020200202020202020200202020202020202220202020202020202";

    function performNonExistingServiceFunctionCall() public {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.delegatecall(abi.encodeWithSelector(FakePrngSystemContract.fakeFunction.selector));
        require(success);
    }

    function performLargeInputCall() public {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.delegatecall(abi.encode(largeInput));
        require(success);
    }

    function performEmptyInputCall() public {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.delegatecall(abi.encode(""));
        require(success);
    }

    function performLessThanFourBytesFunctionCall() public {
        (bool success, bytes memory result) = PRECOMPILE_ADDRESS.delegatecall(abi.encode("0xcdcd"));
        require(success);
    }
}

interface FakePrngSystemContract {
    function fakeFunction() external;
}