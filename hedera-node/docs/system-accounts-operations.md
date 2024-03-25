# System accounts

There are some differences when calling operations on system accounts in Hedera and in Ethereum.
This doc covers these differences and describes the expected behavior.

### ExtCode operations

`ExtCodeSize`

**Ethereum:** Get size of an account’s code. If there’s no code returns 0.

**Hedera:** For addresses in range 0x0 - 0x3E8 (0.0.0 - 0.0.1000) it returns 0.

`ExtCodeCopy`

**Ethereum:** Copy an account’s code to memory. If a contract doesn’t exist on the address, it copies empty bytes.

**Hedera:** For addresses in range 0x0 - 0x3E8 (0.0.0 - 0.0.1000) it copies empty bytes.

`ExtCodeHash`

**Ethereum:** Hash of the chosen account's code, the empty hash (0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470) if the account has no code, or 0 if the account does not exist or has been destroyed.

**Hedera:** For addresses in range 0x0 - 0x3E8 (0.0.0 - 0.0.1000) it returns the empty hash.

### Balance operation

**Ethereum:** Returns balance or 0 if an account or contract doesn’t exist on the address. This is valid for any address.

**Hedera:**
1. For addresses is range 0x0 - 0x2EE (0.0.0 - 0.0.750): returns 0 as if account doesn’t exist.
2. For addresses is range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000): returns balance or 0 if an account or contract doesn’t exist on the address. This behavior is the same for addresses above 0.0.1000.

### SelfDestruct operation

**Ethereum:** success for any address. If the beneficiary is the same as contract address, it burns the eth and destructs the contract.

**Hedera:**
1. For addresses is range 0x0 - 0x2EE (0.0.0 - 0.0.750): fail with status `INVALID_SOLIDITY_ADDRESS`.
2. For addresses is range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000):
   - success, if account exists and has `receiverSigRequired` == false;
   - success, if account exists and has `receiverSigRequired` == false and account is `sender`;
   - fail in some failing scenarios such as: beneficiary same as account-to-be-deleted, `receiverSigRequired` == true and signature provided, etc.
3. For addresses above 0.0.1000: the same as 2. but a hollow account would be created if the beneficiary doesn't exist.

### Call operations (CallOp, DelegateCallOp, CallCodeOp, StaticCallOp)
1. For address 0x0:
   - **Ethereum:** fail, there is no contract on this address.
   - **Hedera:** success with no op.
2. For address range 0x1 - 0x9:
   - **Ethereum:** success, the Ethereum precompiles exist on these addresses.
   - **Hedera:** success, the Ethereum precompiles exist on these addresses.
3. For address range 0xA - 0x166 (0.0.10 - 0.0.358):
   - **Ethereum:**
     - success, if the address is a contract and we are using the correct ABI;
     - fail, if the address is a contract and we are **not** using the correct ABI;
     - fail, if there is no contract.
   - **Hedera:** success with no op.
4. For addresses 0x167, 0x168, 0x169 (0.0.359, 0.0.360, 0.0.361):
   - **Ethereum:**
      - success, if the address is a contract and we are using the correct ABI;
      - fail, if the address is a contract and we are **not** using the correct ABI;
      - fail, if there is no contract.
   - **Hedera:** success, the HTS, ExchangeRate and PRNG precompiles exist on these addresses accordingly.
5. For address range 0x16A - 0x3E8 (0.0.362 - 0.0.1000):
   - **Ethereum:**
      - success, if the address is a contract and we are using the correct ABI;
      - fail, if the address is a contract and we are **not** using the correct ABI;
      - fail, if there is no contract.
   - **Hedera:** success with no op.

### Transfer and send operations

**Ethereum:**
1. For addresses in range 0x0 - 0x9: success, value is locked.
2. For addresses 0xA and above: 
    - success, if address is a payable contract;
    - success, if address is an account;
    - success, if address is empty + creates the account;
    - fail, if address is a non-payable contract.

**Hedera:**
1. For addresses is range 0x0 - 0x2EE (0.0.0 - 0.0.750):
    - Transfers using `.send` and `.transfer`: fail with status `INVALID_FEE_SUBMITTED`. Exception: `tokenCreate` to address 0x167 which is successful.
    - Transfer of HTS tokens through the HTS precompile: fail with status `INVALID_RECEIVING_NODE_ACCOUNT`.
2. For addresses is range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000):
    - success, if account exists and has `receiverSigRequired` == false;
    - success, if account exists and has `receiverSigRequired` == false and account is `sender`;
   
_The account is not created if it doesn't exist._
3. For addresses above 0.0.1000:
    - success, if the address is a payable contract;
    - success, if address is an account with `receiverSigRequired` == false;
    - success, if address is empty + creates the account;
    - fail, if address is an account with `receiverSigRequired` == true;
    - if the address is a non-payable contract:
      - fail for transfers using `.send` and `.transfer`;
      - success for transfer of HTS tokens through the HTS precompile.