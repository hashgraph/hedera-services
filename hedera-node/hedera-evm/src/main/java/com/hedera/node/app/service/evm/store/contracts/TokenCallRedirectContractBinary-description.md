# `TOKEN_CALL_REDIRECT_CONTRACT_BINARY`

The "Token Call Redirect" contract helps implement [HIP-218: "Smart Contract interactions with Hedera Token Accounts"](https://hips.hedera.com/hip/hip-218) by providing a proxy for an HTS token kind (defined by an _account_) into the EVM world so that HTS tokens can behave as if they were ERC-20/ERC-721 tokens. (For a useful subset of ERC-20/ERC-721 operations: See HIP-218 for specifics.)

Each HTS account acts as if it is the address of a Token Call Redirect contract.  This contract simply wraps the calldata (provided by the calling contract or transaction) with the HTS token's own address, then delegate-calls the actual HTS precompile, passing back whatever the return status and value is.

The Token Call Redirect contract acts as if it is unique for each HTS token kind.  A "template" bytecode is in the source code with a fixed bitstring (`TOKEN_BYTECODE_PATTERN == 0xfefefe....fe`) where the HTS token kind address is supposed to be: that bitstring is replaced by the actual HTS token kind address just as it is given to the EVM to process it.

## Raw Bytecode

From [`HederaEvmWorldStateTokenAccount` at `ca5e6ec2`](https://github.com/hashgraph/hedera-services/blob/f023153207482730f79fd5da69c6fbd29eaa3fcc/hedera-node/hedera-evm/src/main/java/com/hedera/node/app/service/evm/store/contracts/HederaEvmWorldStateTokenAccount.java#L35) (as viewed on 2023-09-17)

```
6080604052348015600f57600080fd5b506000610167905077618dc65efefefe
fefefefefefefefefefefefefefefefefe600052366000602037600080366018
016008845af43d806000803e8160008114605857816000f35b816000fdfea264
6970667358221220d8378feed472ba49a0005514ef7087017f707b45fb9bf56b
b81bb93ff19a238b64736f6c634300080b0033
```

Where `fefefe....fefefe` is replaced by an EVM address (HTS token kind's account-num alias (aka: long-zero)).

## Solidity
Via `library.dedaub.com/decompile`:

```solidity
function __function_selector__() public nonPayable { 
    v0 = 0x167.delegatecall(0x618dc65efefefefefefefefefefefefefefefefefefefefe).gas(msg.gas);
    require(v0 != 0); // checks call status, propagates error data on error
    return MEM[0 len (RETURNDATASIZE())];
}
```
## 3-address

```
function __function_selector__() public {
    Begin block 0x0
    prev=[], succ=[0xb, 0xf]
    =================================
    0x0: v0(0x80) = CONST 
    0x2: v2(0x40) = CONST 
    0x4: MSTORE v2(0x40), v0(0x80)
    0x5: v5 = CALLVALUE 
    0x7: v7 = ISZERO v5
    0x8: v8(0xf) = CONST 
    0xa: JUMPI v8(0xf), v7

    Begin block 0xb
    prev=[0x0], succ=[]
    =================================
    0xb: vb(0x0) = CONST 
    0xe: REVERT vb(0x0), vb(0x0)

    Begin block 0xf
    prev=[0x0], succ=[0x58, 0x54]
    =================================
    0x11: v11(0x0) = CONST 
    0x13: v13(0x167) = CONST 
    0x18: v18(0x618dc65efefefefefefefefefefefefefefefefefefefefe) = CONST 
    0x31: v31(0x0) = CONST 
    0x33: MSTORE v31(0x0), v18(0x618dc65efefefefefefefefefefefefefefefefefefefefe)
    0x34: v34 = CALLDATASIZE 
    0x35: v35(0x0) = CONST 
    0x37: v37(0x20) = CONST 
    0x39: CALLDATACOPY v37(0x20), v35(0x0), v34
    0x3a: v3a(0x0) = CONST 
    0x3d: v3d = CALLDATASIZE 
    0x3e: v3e(0x18) = CONST 
    0x40: v40 = ADD v3e(0x18), v3d
    0x41: v41(0x8) = CONST 
    0x44: v44 = GAS 
    0x45: v45 = DELEGATECALL v44, v13(0x167), v41(0x8), v40, v3a(0x0), v3a(0x0)
    0x46: v46 = RETURNDATASIZE 
    0x48: v48(0x0) = CONST 
    0x4b: RETURNDATACOPY v48(0x0), v48(0x0), v46
    0x4d: v4d(0x0) = CONST 
    0x50: v50 = EQ v45, v4d(0x0)
    0x51: v51(0x58) = CONST 
    0x53: JUMPI v51(0x58), v50

    Begin block 0x58
    prev=[0xf], succ=[]
    =================================
    0x5a: v5a(0x0) = CONST 
    0x5c: REVERT v5a(0x0), v46

    Begin block 0x54
    prev=[0xf], succ=[]
    =================================
    0x55: v55(0x0) = CONST 
    0x57: RETURN v55(0x0), v46

}
```

## Disassembly
Via `library.dedaub.com/decompile`:

```
    0x0: PUSH1     0x80
    0x2: PUSH1     0x40
    0x4: MSTORE    
    0x5: CALLVALUE 
    0x6: DUP1      
    0x7: ISZERO    
    0x8: PUSH1     0xf
    0xa: JUMPI     
    0xb: PUSH1     0x0
    0xd: DUP1      
    0xe: REVERT    
    0xf: JUMPDEST  
   0x10: POP       
   0x11: PUSH1     0x0
   0x13: PUSH2     0x167
   0x16: SWAP1     
   0x17: POP       
   0x18: PUSH24    0x618dc65efefefefefefefefefefefefefefefefefefefefe
   0x31: PUSH1     0x0
   0x33: MSTORE    
   0x34: CALLDATASIZE
   0x35: PUSH1     0x0
   0x37: PUSH1     0x20
   0x39: CALLDATACOPY
   0x3a: PUSH1     0x0
   0x3c: DUP1      
   0x3d: CALLDATASIZE
   0x3e: PUSH1     0x18
   0x40: ADD       
   0x41: PUSH1     0x8
   0x43: DUP5      
   0x44: GAS       
   0x45: DELEGATECALL
   0x46: RETURNDATASIZE
   0x47: DUP1      
   0x48: PUSH1     0x0
   0x4a: DUP1      
   0x4b: RETURNDATACOPY
   0x4c: DUP2      
   0x4d: PUSH1     0x0
   0x4f: DUP2      
   0x50: EQ        
   0x51: PUSH1     0x58
   0x53: JUMPI     
   0x54: DUP2      
   0x55: PUSH1     0x0
   0x57: RETURN    
   0x58: JUMPDEST  
   0x59: DUP2      
   0x5a: PUSH1     0x0
   0x5c: REVERT    
   0x5d: INVALID   
   0x5e: LOG2      
   0x5f: PUSH5     0x6970667358
   0x65: MISSING   
   0x66: SLT       
   0x67: SHA3      
   0x68: MISSING   
   0x69: CALLDATACOPY
   0x6a: DUP16     
   0x6b: MISSING   
   0x6c: MISSING   
   0x6d: PUSH19    0xba49a0005514ef7087017f707b45fb9bf56bb8
   0x81: SHL       
   0x82: MISSING   
   0x83: EXTCODEHASH
   0x84: CALL      
   0x85: SWAP11    
   0x86: MISSING   
   0x87: DUP12     
   0x88: PUSH5     0x736f6c6343
   0x8e: STOP      
   0x8f: ADDMOD    
   0x90: SIGNEXTEND
   0x91: STOP      
   0x92: CALLER    
```
