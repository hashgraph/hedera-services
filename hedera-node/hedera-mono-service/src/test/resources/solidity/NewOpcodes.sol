pragma solidity >=0.5.0 <0.6.0;

contract NewOpcodes {


    function opShl(uint _one, uint _two) public pure returns (uint _resp){
        assembly {
            _resp := shl(_one, _two)
        }
    }

    function opShr(uint _one, uint _two) public pure returns (uint _resp){
        assembly {
            _resp := shr(_one, _two)
        }
    }

    function opSar(uint _one, uint _two) public pure returns (uint _resp){
        assembly {
            _resp := sar(_one, _two)
        }
    }

    function opExtCodeHash(address _addr) public view returns (bytes32 _resp){
        assembly {
            _resp := extcodehash(_addr)
        }
    }

}