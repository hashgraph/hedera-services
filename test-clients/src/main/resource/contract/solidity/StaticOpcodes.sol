pragma solidity ^0.5.0;

contract StaticOpcodes {

    function runBlockhash(uint _block) public view returns (uint _resp) {
        assembly {
            _resp := blockhash(_block)
        }
    }

    function runCoinbase() public view returns (uint _resp) {
        assembly {
            _resp := coinbase()
        }
    }

    function runNumber() public view returns (uint _resp) {
        assembly {
            _resp := number()
        }
    }

    function runDifficulty() public view returns (uint _resp) {
        assembly {
            _resp := difficulty()
        }
    }

    function runGaslimit() public view returns (uint _resp) {
        assembly {
            _resp := gaslimit()
        }
    }

    // Counterexample to test non-zero return
    function runAdd() public pure returns (uint _resp) {
        assembly {
            _resp := add(2, 4)
        }
    }
}
