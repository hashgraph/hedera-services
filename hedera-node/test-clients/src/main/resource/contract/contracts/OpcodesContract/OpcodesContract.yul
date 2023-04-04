/// @use-src 0:"OpcodesContract.sol"
object "NewOpcodes_51" {
    code {
        /// @src 0:290:1096  "contract NewOpcodes {..."
        mstore(64, memoryguard(128))
        if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }

        constructor_NewOpcodes_51()

        let _1 := allocate_unbounded()
        codecopy(_1, dataoffset("NewOpcodes_51_deployed"), datasize("NewOpcodes_51_deployed"))

        return(_1, datasize("NewOpcodes_51_deployed"))

        function allocate_unbounded() -> memPtr {
            memPtr := mload(64)
        }

        function revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() {
            revert(0, 0)
        }

        /// @src 0:290:1096  "contract NewOpcodes {..."
        function constructor_NewOpcodes_51() {

            /// @src 0:290:1096  "contract NewOpcodes {..."

        }
        /// @src 0:290:1096  "contract NewOpcodes {..."

    }
    /// @use-src 0:"OpcodesContract.sol"
    object "NewOpcodes_51_deployed" {
        code {
            /// @src 0:290:1096  "contract NewOpcodes {..."
            mstore(64, 128)

            if iszero(lt(calldatasize(), 4))
            {
                let selector := shift_right_224_unsigned(calldataload(0))
                switch selector

                case 0x1bdbf00f
                {
                    // opExtCodeHash(address)

                    external_fun_opExtCodeHash_43()
                }

                case 0x4cb909a3
                {
                    // opShl(uint256,uint256)

                    external_fun_opShl_12()
                }

                case 0x8d71c7ff
                {
                    // opSar(uint256,uint256)

                    external_fun_opSar_34()
                }

                case 0x99174e14
                {
                    // opPush0()

                    external_fun_opPush0_50()
                }

                case 0xb20ba9b1
                {
                    // opShr(uint256,uint256)

                    external_fun_opShr_23()
                }

                default {}
            }

            revert_error_42b3090547df1d2001c96683413b8cf91c1b902ef5e3cb8d9f6f304cf7446f74()

            function shift_right_224_unsigned(value) -> newValue {
                newValue :=

                shr(224, value)

            }

            function allocate_unbounded() -> memPtr {
                memPtr := mload(64)
            }

            function revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() {
                revert(0, 0)
            }

            function revert_error_dbdddcbe895c83990c08b3492a0e83918d802a52331272ac6fdb6a7c4aea3b1b() {
                revert(0, 0)
            }

            function revert_error_c1322bf8034eace5e0b5c7295db60986aa89aae5e0ea0873e4689e076861a5db() {
                revert(0, 0)
            }

            function cleanup_t_uint160(value) -> cleaned {
                cleaned := and(value, 0xffffffffffffffffffffffffffffffffffffffff)
            }

            function cleanup_t_address(value) -> cleaned {
                cleaned := cleanup_t_uint160(value)
            }

            function validator_revert_t_address(value) {
                if iszero(eq(value, cleanup_t_address(value))) { revert(0, 0) }
            }

            function abi_decode_t_address(offset, end) -> value {
                value := calldataload(offset)
                validator_revert_t_address(value)
            }

            function abi_decode_tuple_t_address(headStart, dataEnd) -> value0 {
                if slt(sub(dataEnd, headStart), 32) { revert_error_dbdddcbe895c83990c08b3492a0e83918d802a52331272ac6fdb6a7c4aea3b1b() }

                {

                    let offset := 0

                    value0 := abi_decode_t_address(add(headStart, offset), dataEnd)
                }

            }

            function cleanup_t_bytes32(value) -> cleaned {
                cleaned := value
            }

            function abi_encode_t_bytes32_to_t_bytes32_fromStack(value, pos) {
                mstore(pos, cleanup_t_bytes32(value))
            }

            function abi_encode_tuple_t_bytes32__to_t_bytes32__fromStack(headStart , value0) -> tail {
                tail := add(headStart, 32)

                abi_encode_t_bytes32_to_t_bytes32_fromStack(value0,  add(headStart, 0))

            }

            function external_fun_opExtCodeHash_43() {

                if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }
                let param_0 :=  abi_decode_tuple_t_address(4, calldatasize())
                let ret_0 :=  fun_opExtCodeHash_43(param_0)
                let memPos := allocate_unbounded()
                let memEnd := abi_encode_tuple_t_bytes32__to_t_bytes32__fromStack(memPos , ret_0)
                return(memPos, sub(memEnd, memPos))

            }

            function cleanup_t_uint256(value) -> cleaned {
                cleaned := value
            }

            function validator_revert_t_uint256(value) {
                if iszero(eq(value, cleanup_t_uint256(value))) { revert(0, 0) }
            }

            function abi_decode_t_uint256(offset, end) -> value {
                value := calldataload(offset)
                validator_revert_t_uint256(value)
            }

            function abi_decode_tuple_t_uint256t_uint256(headStart, dataEnd) -> value0, value1 {
                if slt(sub(dataEnd, headStart), 64) { revert_error_dbdddcbe895c83990c08b3492a0e83918d802a52331272ac6fdb6a7c4aea3b1b() }

                {

                    let offset := 0

                    value0 := abi_decode_t_uint256(add(headStart, offset), dataEnd)
                }

                {

                    let offset := 32

                    value1 := abi_decode_t_uint256(add(headStart, offset), dataEnd)
                }

            }

            function abi_encode_t_uint256_to_t_uint256_fromStack(value, pos) {
                mstore(pos, cleanup_t_uint256(value))
            }

            function abi_encode_tuple_t_uint256__to_t_uint256__fromStack(headStart , value0) -> tail {
                tail := add(headStart, 32)

                abi_encode_t_uint256_to_t_uint256_fromStack(value0,  add(headStart, 0))

            }

            function external_fun_opShl_12() {

                if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }
                let param_0, param_1 :=  abi_decode_tuple_t_uint256t_uint256(4, calldatasize())
                let ret_0 :=  fun_opShl_12(param_0, param_1)
                let memPos := allocate_unbounded()
                let memEnd := abi_encode_tuple_t_uint256__to_t_uint256__fromStack(memPos , ret_0)
                return(memPos, sub(memEnd, memPos))

            }

            function external_fun_opSar_34() {

                if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }
                let param_0, param_1 :=  abi_decode_tuple_t_uint256t_uint256(4, calldatasize())
                let ret_0 :=  fun_opSar_34(param_0, param_1)
                let memPos := allocate_unbounded()
                let memEnd := abi_encode_tuple_t_uint256__to_t_uint256__fromStack(memPos , ret_0)
                return(memPos, sub(memEnd, memPos))

            }

            function abi_decode_tuple_(headStart, dataEnd)   {
                if slt(sub(dataEnd, headStart), 0) { revert_error_dbdddcbe895c83990c08b3492a0e83918d802a52331272ac6fdb6a7c4aea3b1b() }

            }

            function external_fun_opPush0_50() {

                if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }
                abi_decode_tuple_(4, calldatasize())
                let ret_0 :=  fun_opPush0_50()
                let memPos := allocate_unbounded()
                let memEnd := abi_encode_tuple_t_uint256__to_t_uint256__fromStack(memPos , ret_0)
                return(memPos, sub(memEnd, memPos))

            }

            function external_fun_opShr_23() {

                if callvalue() { revert_error_ca66f745a3ce8ff40e2ccaf1ad45db7774001b90d25810abd9040049be7bf4bb() }
                let param_0, param_1 :=  abi_decode_tuple_t_uint256t_uint256(4, calldatasize())
                let ret_0 :=  fun_opShr_23(param_0, param_1)
                let memPos := allocate_unbounded()
                let memEnd := abi_encode_tuple_t_uint256__to_t_uint256__fromStack(memPos , ret_0)
                return(memPos, sub(memEnd, memPos))

            }

            function revert_error_42b3090547df1d2001c96683413b8cf91c1b902ef5e3cb8d9f6f304cf7446f74() {
                revert(0, 0)
            }

            function zero_value_for_split_t_uint256() -> ret {
                ret := 0
            }

            /// @ast-id 12
            /// @src 0:318:460  "function opShl(uint _one, uint _two) public pure returns (uint _resp){..."
            function fun_opShl_12(var__one_3, var__two_5) -> var__resp_8 {
                /// @src 0:376:386  "uint _resp"
                let zero_t_uint256_1 := zero_value_for_split_t_uint256()
                var__resp_8 := zero_t_uint256_1

                /// @src 0:397:454  "assembly {..."
                {
                    var__resp_8 := shl(var__one_3, var__two_5)
                }

            }
            /// @src 0:290:1096  "contract NewOpcodes {..."

            /// @ast-id 23
            /// @src 0:466:608  "function opShr(uint _one, uint _two) public pure returns (uint _resp){..."
            function fun_opShr_23(var__one_14, var__two_16) -> var__resp_19 {
                /// @src 0:524:534  "uint _resp"
                let zero_t_uint256_2 := zero_value_for_split_t_uint256()
                var__resp_19 := zero_t_uint256_2

                /// @src 0:545:602  "assembly {..."
                {
                    var__resp_19 := shr(var__one_14, var__two_16)
                }

            }
            /// @src 0:290:1096  "contract NewOpcodes {..."

            /// @ast-id 34
            /// @src 0:614:756  "function opSar(uint _one, uint _two) public pure returns (uint _resp){..."
            function fun_opSar_34(var__one_25, var__two_27) -> var__resp_30 {
                /// @src 0:672:682  "uint _resp"
                let zero_t_uint256_3 := zero_value_for_split_t_uint256()
                var__resp_30 := zero_t_uint256_3

                /// @src 0:693:750  "assembly {..."
                {
                    var__resp_30 := sar(var__one_25, var__two_27)
                }

            }
            /// @src 0:290:1096  "contract NewOpcodes {..."

            function zero_value_for_split_t_bytes32() -> ret {
                ret := 0
            }

            /// @ast-id 43
            /// @src 0:762:911  "function opExtCodeHash(address _addr) public view returns (bytes32 _resp){..."
            function fun_opExtCodeHash_43(var__addr_36) -> var__resp_39 {
                /// @src 0:821:834  "bytes32 _resp"
                let zero_t_bytes32_4 := zero_value_for_split_t_bytes32()
                var__resp_39 := zero_t_bytes32_4

                /// @src 0:845:905  "assembly {..."
                {
                    var__resp_39 := extcodehash(var__addr_36)
                }

            }
            /// @src 0:290:1096  "contract NewOpcodes {..."

            /// @ast-id 50
            /// @src 0:917:1093  "function opPush0() public pure returns (uint _resp) {..."
            function fun_opPush0_50() -> var__resp_46 {
                /// @src 0:957:967  "uint _resp"
                let zero_t_uint256_5 := zero_value_for_split_t_uint256()
                var__resp_46 := zero_t_uint256_5

                /// @src 0:979:1087  "assembly {..."
                {
                    var__resp_46 := add(verbatim_0i_1o(hex"5f"), 0x5f)
                }

            }
            /// @src 0:290:1096  "contract NewOpcodes {..."

        }

        data ".metadata" hex"a26469706673582212206958d5e8033a3e87ef7a87a4f1d9582db215722a80764baf6e9c1909ebca2a4764736f6c63430008120033"
    }

}


