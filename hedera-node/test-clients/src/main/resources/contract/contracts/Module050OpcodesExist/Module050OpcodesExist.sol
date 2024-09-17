// SPDX-License-Identifier: Apache-2.0
pragma solidity >=0.8.24;

// Methods either succeed (Cancun) or fail (earlier) - nothing needs be returned

contract Module050OpcodesExist {
    function try_transient_storage() public {
        assembly {
            tstore(10, 11111)
            pop(tload(10))
        }
    }

    function try_mcopy() public pure {
        bytes memory byts = "Hello, World!";
        assembly {
           mcopy(0,0,32)
        }
    }

    // If the KZG precompile _exists_ it will fail given the bad input data, if it _doesn't_ exist
    // it will succeed (doing nothing).  But we prefer that _reversed_ so that Cancun succeeds,
    // earlier EVMs fail.  That's done by asserting the negative.
    function try_kzg_precompile() public view {
        bytes memory byts = "Hello, World!";

        (bool success, ) = address(0x0A).staticcall(byts);
        assert(!success);
    }

    // Run an actual test case through the KZG precompile, one known to result in SUCCESS.  (Test
    // case taken from BESU's `pointEvaluationPrecompile.json`.)
    function kzg_precompile_success_case() public view {
        bytes memory input = hex"010657f37554c781402a22917dee2f75def7ab966d7b770905398eba3c444014623ce31cf9759a5c8daf3a357992f9f3dd7f9339d8998bc8e68373e54f00b75e0000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000c00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000";
        bytes memory expected = hex"000000000000000000000000000000000000000000000000000000000000100073eda753299d7d483339d80809a1d80553bda402fffe5bfeffffffff00000001";

        (bool success, bytes memory actual) = address(0x0A).staticcall(input);
        assert(success);
        assert(actual.length == 64);
        assert(keccak256(abi.encodePacked(expected)) == keccak256(abi.encodePacked(actual)));
    }
}
