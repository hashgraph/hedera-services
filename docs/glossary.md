 Glossary of Technical Terms
---

## A
- [Account Aliases](#account-aliases)
- [Admin Transaction](#admin-transaction)
- [Auto Associations](#asynchronous)
- [Auto-Renew Accounts](#auto-renew-accounts)
- [Address Book](#address-book)
- [Application Accounts](#application-accounts)

## B
- [BLS](#bls)
- [Block Proof](#block-proof)
- [Block Signature](#block-signature)
- [Block Stream](#block-stream)

## C
- [Civilian Accounts](#civilian-accounts)
- [Config.txt](#config.txt)
- [Congestion Pricing](#congestion-pricing)
- [Consensus Time](#consensus-time)

## D
- [Delegate Call](#delegate-call)

## E
- [EOA](#eoa)

## H
- [Hollow Accounts](#hollow-accounts)

## L
- [Leaky Tests](#leaky-tests)

## P
- [Precompiles](#precompiles)

## S
- [Security Model V2](#security-model-v2)
- [Sidecars](#sidecars)
- [Synthetic Transaction](#synthetic-transactions)
- [System Contracts](#system-contracts)
- [System Transaction](#system-transaction)

## T
- [Token Allowance](#token-allowance)

## W
- [Weibar](#weibar)

---

## Address Book
**Address Book**: The _**network**_ address book is data structure that contains information (such as node ID, alias, IP address, and weight) for all nodes in the network, 
not just consensus, but also block nodes, future validator nodes and more.
The address book is stored as a file on disk at the moment but will be stored purely in state as part of Dynamic Address Book phase 3.	Disambiguation: There is also an "address book" in files 0.0.101 and 0.0.102 which is different to the "network address book" which are manually managed by devops.


## Account Aliases
**Account Aliases**: These are identifiers which are public key values, and are mostly 33 byte EVM address values. The "triplet" form (e.g. 0.0.18273892) is the simpler and easier to "type".
An alias is not intended to be user-friendly, but instead to offer a smart-contract friendly identifier. See https://hips.hedera.com/hip/hip-32 and https://hips.hedera.com/hip/hip-583 for more information.


## Admin Transaction 
**Admin Transaction**: A HAPI transaction which has been signed by the council, for example a Freeze Transaction


## Application Accounts
**Application Accounts:** Used by developers and users interacting with applications deployed on Hedera. These accounts enable users to interact with the functionality provided by dApps, such as gaming, finance, or other decentralized services.

## Auto Associations
**Auto Associations**: The ability of accounts to automatically associate with tokens. An auto association is one or more slots you approve that allow tokens to be sent to your contract or account without explicit authorization for each token type. If this property is not set, you must approve each token manually (via the `TokenAssociateTransaction` in the SDKs) before it can be received, held, or sent by that account.
https://hips.hedera.com/hip/hip-904 - currently in progress, will represent a significant change which allows for the number of "automatic association" slots to be set to -1 which represents infinite slots.


## Auto-Renew Accounts
**Auto-Renew Accounts**: A feature designed to automatically renew the lifecycle of certain entities (like accounts, files, smart contracts, topics, or tokens) by funding their renewal fees. This feature is particularly useful for entities that require continuous operation over long periods, as it automates the process of keeping these entities active by periodically charging a linked auto-renew account. 
See https://hips.hedera.com/hip/hip-16 and https://hips.hedera.com/hip/hip-372 for more information. Note that rent and expiration are not currently enabled but will be in the future.

---

## BLS Signature Algorithm
**Boneh-Lynn-Shacham (BLS)**: A paired-elliptic-curve based signature algorithm with attractive characteristics for aggregate signatures (signatures produced by aggregating many individual signatures, and verifiable with a single public key).  BLS comes from the names of the authors of the initial research paper (Dan Boneh, Ben Lynn, Hovav Shacham). Authors Paper here: [Link](https://link.springer.com/article/10.1007/s00145-004-0314-9)

## Block Proof
**Block Proof**: Cryptographic proof that a particular block contains a given block item. Block proofs will also be the basis of State Proofs as the root hash of states will be included in each block. see

## Block Signature
**Block Signature**: Aggregated (BLS) signature of the hash of Block stream merkle root. Appended to the end of a block stream it is used to verify the block was produced by the majority weight of the network. These signatures will also be used for Invalid State Detection.

## Block Stream
**Block Stream**: A fully verifiable stream of data consisting of individual blocks corresponding to consensus rounds. Each block contains all events, transactions, and state changes that occurred within one consensus round. A block is transmitted from consensus node to block nodes as a stream of individual block items, which form a merkle tree that is verified by a Block Signature. Each block is connected to the previous block by a Block Hash which, together form a block chain.

---

## Child Transaction
**Child Transaction**: A [Synthetic Transaction](#synthetic-transactions) that the EVM or Services initiates as a result of fulfilling a user-initiated transaction (e.g. [Hollow Accounts](#hollow-accounts) creation, or a scheduled execution).

## Civilian Accounts
**Civilian Accounts**:  User accounts that are not associated with any special roles or permissions beyond standard user functionality. These are mostly used for testing purposes. By contrast, System Accounts and Node Accounts are accounts that have specific roles and responsibilities within the infrastructure and governance of the Hedera network.

## Config.txt
**Config.txt**: A text file loaded by the platform on startup if no valid state is found on disk. This file contains the address book to use when starting from genesis.

## Congestion Pricing
**Congestion Pricing**: A mechanism designed to manage network congestion by dynamically adjusting transaction fees based on network demand. The primary goal of congestion pricing is to discourage excessive network usage during peak times. Refer to [Congestion Pricing](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/docs/fees/automated-congestion-pricing.md) and [Fees](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/docs/design/app/fees.md).

## Consensus Time
**Consensus Time**: The timestamp assigned to a transaction once it has reached consensus across the network. This timestamp indicates the moment when the network agrees on the order and validity of the transaction, ensuring that all participants have a consistent view of the transaction sequence. See [Consensus Service](https://github.com/hashgraph/hedera-services/blob/develop/hedera-node/docs/design/services/consensus-service/consensus-service.md) for more information.

---

## Delegate Call
**Delegate Call**: Used to call a function in another contract, but with the context of the calling contract. This allows for the execution of functions in one contract as if they were part of another but retains the original caller’s context (msg.sender and msg.value). See [Delegate Call](https://medium.com/@solidity101/understanding-call-delegatecall-and-staticcall-primitives-in-ethereum-smart-contracts-dfff21caa727) for more information.
 
---

## EOA
**EOA (Externally Owned Accounts)**: Accounts that are controlled by private keys and are not associated with smart contracts. These accounts are used to send and receive transactions on the network and interact with smart contracts. In contrast to contract accounts or token accounts, EOAs are typically owned by individual users and are used to manage their assets and participate in the network.

---

## Hollow Accounts
**Hollow Accounts**: An account that has been created with an account number and alias but lacks an account key. This term is specifically used to describe accounts are auto-created by doing a CryptoTransfer to an alias that is of EVM address size, which allows applications to create user accounts instantly, even without an internet connection, by assigning an account alias. See also [Account Aliases](#account-aliases), https://hips.hedera.com/hip/hip-32 and https://hips.hedera.com/hip/hip-583. 

---

## Leaky Tests
**Leaky Tests**: Are unit or other tests that should not be run in parallel because they can “leak” some of their state or data out into the global state.

---

## Precompiles
**Precompiles**: Behave as if they were contracts deployed at specific addresses (with one exception: The EIP-4788 "beacon roots contract" which is a real contract deployed at a specific address) In Ethereum, they are very low addresses, 0x00 .. 0x0F). 
They are not implemented as contracts because that would be far too slow, instead they are built-in to the code of the Ethereum EVM, which checks to see if the address you're calling is one of the precompiles and then just runs the related code.
We call our equivalent [System Contracts](#system-contracts) but there may still some legacy references to "precompiles" in the codebase and older documentation.

---

## Security Model V2
**Security Model v2**: Hedera introduced the HSCS Security Model v2 to enhance the security of its network building with improved features to better safeguard against potential vulnerabilities and attacks.  By incorporating enhanced cryptographic techniques, robust consensus mechanisms, decentralized governance, and continuous monitoring.
Key features include - Restrictions on Smart contracts Storage; Prohibition on Delegate Calls to System Smart Contracts; Limited Access to EOAs' Storage; Token Allowance Requirement for Balance Changes.
Refer to this [official blog post](https://hedera.com/blog/hedera-smart-contract-service-security-model-update) for more information.

## Sidecars
**Sidecars**: In Services, a sidecar refers to additional records that are created alongside the main transaction records. These sidecar records provide more detailed information about the transactions, making it easier to debug and understand the state changes that occur as a result of the transactions. Note that these are related to the current record stream implementation and will be replaced by block streams in the future.

## Synthetic Transaction
**Synthetic Transaction**: Any transaction that is neither submitted through HAPI nor
  created by the platform. An example of which is the the deletion of an expired
  entity that did not pay rent. Synthetic transactions are presented in the block
  stream (and record files) as a non-system transaction with a non-zero nonce
  **or** `scheduled = true` in the `TransactionID` value.
  Note that the use of a scheduled flag instead of a non-zero nonce for scheduled
  transactions is for legacy reasons.


## System Contracts
**System Contracts**:  Smart contracts that have a permanent address in the network, meaning they will always be available at the same contract address. One example is the Token Service System Contract which allows smart contracts on Hedera to interact with the Hedera Token Service, offering functionalities like token creation, burning, and minting through the EVM

## System Transaction
**System Transaction**: A transaction created by the platform (not services) software. An example of which is a node's signature on a state. Inside an event, every transaction has a flag saying whether it is a system transaction or not.
## Admin Transaction
**Admin Transaction**: a HAPI transaction signed by the council. Such as freeze.
---

## Token Allowance
**Token Allowance**: A feature that allows an account (the owner) to authorize another account (the spender) to transfer tokens on its behalf up to a specified limit. This mechanism is similar to the ERC-20 allowance mechanism used in Ethereum.
The amount of tokens that a user has authorized a smart contract to spend on their behalf. This allowance is set by the user and can be modified or revoked at any time. It allows smart contracts to spend tokens on behalf of the user without requiring explicit approval for each transaction.

---

## Weibar
**WeiBar**: 1 hbar is equivalent to 100,000,000 (100 million) weibars. The unit of hbar used when you come in via an ethereum transaction - it's like wei is to ether.
