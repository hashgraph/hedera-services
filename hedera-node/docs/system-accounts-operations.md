# System accounts

There are some differences when calling operations on system accounts in Hedera and in Ethereum.
This doc covers these differences and describes the expected behavior.

### ExtCode operations

`ExtCodeSize`

**Ethereum:** Get size of an account’s code. If there’s no code, it returns 0.

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
1. For addresses in range 0x0 - 0x2EE (0.0.0 - 0.0.750): returns 0 as if account doesn’t exist.
2. For addresses in range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000): returns balance or 0 if an account or contract doesn’t exist on the address. This behavior is the same for addresses above 0.0.1000.

| Address                            | in Ethereum                                                                   | in Hedera                                                                     |
|------------------------------------|-------------------------------------------------------------------------------|-------------------------------------------------------------------------------|
| 0x0                                | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0x1 → 0x9                          | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0xA → 0x166                        | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0x167 (0.0.359)                    | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0x168, 0x169 (0.0.360, 0.0.361)    | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0x16a → 0x2EE (0.0.361 → 0.0.750)  | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns 0 as if account doesn’t exist                                       |
| 0x2EF → 0x3E8 (0.0.751 → 0.0.1000) | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns balance or 0 if an account or contract doesn’t exist on the address |
| 0x3E8 → ♾️                         | ✅ returns balance or 0 if an account or contract doesn’t exist on the address | ✅ returns balance or 0 if an account or contract doesn’t exist on the address |

### SelfDestruct operation

**Ethereum:** success for any address. If the beneficiary is the same as contract address, it burns the eth and destructs the contract.

**Hedera:**
1. For addresses in range 0x0 - 0x2EE (0.0.0 - 0.0.750): fail with status `INVALID_SOLIDITY_ADDRESS`.
2. For addresses in range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000):
   - success, if account exists and has `receiverSigRequired` == false;
   - success, if account exists and has `receiverSigRequired` == true and account is `sender`;
   - fail in some failing scenarios such as: beneficiary same as account-to-be-deleted, `receiverSigRequired` == true and signature provided, etc.
3. For addresses above 0.0.1000: the same as 2. but a hollow account would be created if the beneficiary doesn't exist.

| Address                              |                in Ethereum                 | in Hedera                                                                                                                                                                                                                                                                                     |
|--------------------------------------|:------------------------------------------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0x0                                  |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0x1 → 0x9                            |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0xA → 0x166                          |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0x167 (0.0.359)                      |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0x168, 0x169 (0.0.360, 0.0.361)      |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0x16a → 0x2EE (0.0.362 → 0.0.750)    |                     ✅                      | ❌ - INVALID_SOLIDITY_ADDRESS                                                                                                                                                                                                                                                                  |
| 0x2EF → 0x3E8 (0.0.751 → 0.0.1000)   |                     ✅                      | ✅ - if account exists [0.0.800-0.0.999]<br/>❌ - INVALID_SOLIDITY_ADDRESS if account does not exist [0.0.751 - 0.0.799]<br/>❌ - some failing scenarios (beneficiary same as account-to-be-deleted, receiverSigRequired and no signature provided, etc)                                     |
| 0x3E8 → ♾️                           |                     ✅                      | ✅ - if account exists and has receiverSigRequired = false<br/>✅ - if (account exists) and (account has receiverSigRequired = true) and (account is sender)<br/>❌ - some failing scenarios (beneficiary same as account-to-be-deleted, receiverSigRequired and no signature provided, etc) |
| beneficiary same as contract address | ✅ burns the eth and destructs the contract | ❌ - fails with SELF_DESTRUCT_TO_SELF                                                                                                                                                                                                                                                          |

### Call operations (CallOp, DelegateCallOp, CallCodeOp, StaticCallOp)

1. For address 0x0:
   - **Ethereum:** success with no op. There is no contract on this address. It is often associated with token burn & mint/genesis events and used as a generic null address.
   - **Hedera:** success with no op.
2. For address range 0x1 - 0x9:
   - **Ethereum:** success, the Ethereum precompiles exist on these addresses.
   - **Hedera:** success, the Ethereum precompiles exist on these addresses.
3. For address range 0xA - 0x166 (0.0.10 - 0.0.358):
   - **Ethereum:**
     - success, if the address is a contract, and we are using the correct ABI;
     - success with no op, if there is no contract;
     - fail, if the address is a contract, and we are **not** using the correct ABI.
   - **Hedera:** success with no op.
4. For addresses 0x167, 0x168, 0x169 (0.0.359, 0.0.360, 0.0.361):
   - **Ethereum:**
     - success, if the address is a contract, and we are using the correct ABI;
     - success with no op, if there is no contract;
     - fail, if the address is a contract, and we are **not** using the correct ABI.
   - **Hedera:** success, the HTS, ExchangeRate and PRNG precompiles exist on these addresses accordingly.
5. For address range 0x16A - 0x3E8 (0.0.362 - 0.0.1000):
   - **Ethereum:**
     - success, if the address is a contract, and we are using the correct ABI;
     - success with no op, if there is no contract;
     - fail, if the address is a contract, and we are **not** using the correct ABI.
   - **Hedera:** success with no op.

_Please note that the expected behavior above is valid considering there is no `value` passed. If there is `value`, this falls in the next section._

| Address                            | Calls in Ethereum (ABI)                                                                                                                                                            | Calls in Hedera (ABI)                                                                                                    |
|------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------------------------------------------|
| 0x0                                | ✅ - success with no op, there is no contract on this address                                                                                                                       | ✅ - success with no op                                                                                                   |
| 0x1 → 0x9                          | ✅ - Ethereum precompiles                                                                                                                                                           | ✅ - Ethereum precompiles                                                                                                 |
| 0xA → 0x166 (0.0.10 → 0.0.358)     | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract<br/>❌ - if address is a contract and we’re not using the correct ABI | ✅ - success with no op                                                                                                   |
| 0x167 (0.0.359)                    | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract<br/>❌ - if address is a contract and we’re not using the correct ABI | ✅ - HTS Precompile                                                                                                       |
| 0x168, 0x169 (0.0.360, 0.0.361)    | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract<br/>❌ - if address is a contract and we’re not using the correct ABI | ✅ - ExchangeRate and PRNG Precompiles                                                                                    |
| 0x16a → 0x2EE (0.0.362 → 0.0.750)  | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract<br/>❌ - if address is a contract and we’re not using the correct ABI | ✅ - success with no op                                                                                                   |
| 0x2EF → 0x3E8 (0.0.751 → 0.0.1000) | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract<br/>❌ - if address is a contract and we’re not using the correct ABI | ✅ - success with no op                                                                                                   |
| 0x3E8 → ♾️ | ✅ - if address is a contract and we’re using the correct ABI<br/>✅ - success with no op, if there is no contract                                                                 | ✅ - if a contract exists and we’re using the correct ABI<br/>❌ - INVALID_SOLIDITY_ADDRESS if a contract does not exist |

### Transfer and send operations

**Ethereum:**
1. For addresses in range 0x0 - 0x9: success, value is locked.
2. For addresses 0xA and above: 
    - success, if address is a payable contract;
    - success, if address is an account;
    - success, if address is empty + creates the account;
    - fail, if address is a non-payable contract.

**Hedera:**
1. For addresses in range 0x0 - 0x2EE (0.0.0 - 0.0.750):
    - Transfers using `.send` and `.transfer`: fail with status `INVALID_FEE_SUBMITTED`. Exception: `tokenCreate` to address 0x167 which is successful.
    - Transfer of HTS tokens through the HTS precompile: fail with status `INVALID_RECEIVING_NODE_ACCOUNT`.
2. For addresses in range 0x2EF - 0x3E8 (0.0.751 - 0.0.1000):
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

| Address                            | Transfers in Ethereum (.send, .transfer)                                                                                                                              | Transfers in Hedera (.send, .transfer)                                                                                                                                                                                                                                  | Transfers of HTS tokens through the HTS precompile                                                                                                                                                                                                                     |
|------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 0x0                                | ✅ - value is locked                                                                                                                                                   | ❌ - INVALID_FEE_SUBMITTED                                                                                                                                                                                                                                               | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0x1 → 0x9                          | ✅ - value is locked                                                                                                                                                   | ❌ - INVALID_FEE_SUBMITTED                                                                                                                                                                                                                                               | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0xA → 0x166 (0.0.10 → 0.0.358)     | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ❌ - INVALID_FEE_SUBMITTED                                                                                                                                                                                                                                               | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0x167 (0.0.359)                    | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ✅ - Ok only for function tokenCreate(..)<br/>❌ - INVALID_FEE_SUBMITTED for all other function calls                                                                                                                                                                   | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0x168, 0x169 (0.0.360, 0.0.361)    | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ❌ - INVALID_FEE_SUBMITTED                                                                                                                                                                                                                                               | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0x16a → 0x2EE (0.0.362 → 0.0.750)  | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ❌ - INVALID_FEE_SUBMITTED                                                                                                                                                                                                                                               | ❌ - INVALID_RECEIVING_NODE_ACCOUNT                                                                                                                                                                                                                                     |
| 0x2EF → 0x3E8 (0.0.751 → 0.0.1000) | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ✅ - if account exists and has receiverSigRequired = false<br/>✅ - if (account exists) and (account has receiverSigRequired = true) and (account is sender)<br/>❗- don’t auto create if it doesn’t exist                                                             | ✅ - if account exists and has receiverSigRequired = false<br/>✅ - if (account exists) and (account has receiverSigRequired = true) and (account is sender)<br/>❗- don’t auto create if it doesn’t exist                    |
| 0x3E8 → ♾️ | ✅ - if address is a payable contract<br/>✅ - if address is an account<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract | ✅ - if address is a payable contract<br/>✅ - if address is an account with receiversigrequired = false<br/>✅ - if address is empty, creates account<br/>❌ - if address is a non-payable contract<br/>❌ - if address is an account with receiversigrequired=true | ✅ - if address is a payable contract<br/>✅ - if address is an account with receiversigrequired = false<br/>✅ - if address is empty, creates account<br/>✅ - if address is a non-payable contract<br/>❌ - if address is an account with receiversigrequired=true |
