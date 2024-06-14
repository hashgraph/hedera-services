 Glossary of Technical Terms
---

## A
- [Account Aliases](#account-aliases)
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
- [Synthetic Transactions](#synthetic-transactions)
- [System Contracts](#system-contracts)
- [System Transaction](#system-transaction)

## T
- [Token Allowance](#token-allowance)

## W
- [Weibar](#weibar)

---

## Address Book
**Address Book**: The data structure that contains data for each consensus node in the network, including node ID, alias, IP address, and weight.

## Account Aliases
**Account Aliases**: A user-friendly identifier that can be classified as either a public key or an Ethereum Virtual Machine (EVM) address. Instead of inputting a long string of characters, users can use simpler and more memorable aliases.

## Application Accounts
**Application Accounts:** Used by developers and users interacting with applications deployed on Hedera. These accounts enable users to interact with the functionality provided by dApps, such as gaming, finance, or other decentralized services.

## Auto Associations
**Auto Associations**: The ability of accounts to automatically associate with tokens. An auto association slot is one or more slots you approve that allow tokens to be sent to your contract without explicit authorization for each token type. If this property is not set, you must approve each token before it is transferred to the contract for the transfer to be successful via the `TokenAssociateTransaction` in the SDKs.

## Auto-Renew Accounts
**Auto-Renew Accounts**: A feature designed to automatically renew the lifecycle of certain entities (like accounts, files, smart contracts, topics, or tokens) by funding their renewal fees. This feature is particularly useful for entities that require continuous operation over long periods, as it automates the process of keeping these entities active by periodically charging a linked auto-renew account.

---

## BLS
**Boneh-Lynn-Shacham (BLS)**: A paired-elliptic-curve based signature algorithm with attractive characteristics for aggregate signatures (signatures produced by aggregating many individual signatures, and verifiable with a single public key).  BLS comes from the names of the authors of the initial research paper (Dan Boneh, Ben Lynn, Hovav Shacham).

## Block Proof
**Block Proof**: Cryptographic proof that a particular block contains a given block item. Block proofs will also be the basis of State Proofs as the root hash of states will be included in each block. see

## Block Signature
**Block Signature**: Aggregated (BLS) signature of the hash of Block stream merkle root. Appended to the end of a block stream it is used to verify the block was produced by the majority weight of the network. These signatures will also be used for Invalid State Detection.

## Block Stream
**Block Stream**: A fully verifiable stream of data consisting of individual blocks corresponding to consensus rounds. Each block contains all events, transactions, and state changes that occurred within one consensus round. A block is transmitted from consensus node to block nodes as a stream of individual block items, which form a merkle tree that is verified by a Block Signature. Each block is connected to the previous block by a Block Hash which, together form a block chain.

---


## Civilian Accounts
**Civilian Accounts**:  User accounts that are not associated with any special roles or permissions beyond standard user functionality. By contrast, System Accounts and Node Accounts are specialized accounts that have specific roles and responsibilities within the infrastructure and governance of the Hedera network.

## Config.txt
**Config.txt**: A text file loaded by the platform on startup if no valid state is found on disk. This file contains the address book to use when starting from genesis.

## Congestion Pricing
**Congestion Pricing**: A mechanism designed to manage network congestion by dynamically adjusting transaction fees based on network demand. The primary goal of congestion pricing is to discourage excessive network usage during peak times.

## Consensus Time
**Consensus Time**: The timestamp assigned to a transaction once it has reached consensus across the network. This timestamp indicates the moment when the network agrees on the order and validity of the transaction, ensuring that all participants have a consistent view of the transaction sequence.

---

## Delegate Call
**Delegate Call**: Used to call a function in another contract, but with the context of the calling contract. This allows for the execution of functions in one contract as if they were part of another but retains the original caller’s context (msg.sender and msg.value).

---

## EOA
**EOA (Externally Owned Accounts)**: Accounts that are controlled by private keys and are not associated with smart contracts. These accounts are used to send and receive transactions on the network and interact with smart contracts. EOAs are typically owned by individual users and are used to manage their assets and participate in the network.

---

## Hollow Accounts
**Hollow Accounts**: An account that has been created with an account number and alias but lacks an account key. This term is specifically used to describe accounts generated through the Auto Account Creation feature, which allows applications to create user accounts instantly, even without an internet connection, by assigning an account alias.

---

## Leaky Tests
**Leaky Tests**: Are unit or other tests that should not be run in parallel because they can “leak” some of their state or data out into the global state.

---

## Precompiles
**Precompiles**: Contracts that are compiled and deployed on the blockchain before they are executed. They are used to optimize the execution of certain operations that are computationally expensive or require a large amount of gas to execute. Precompiled contracts are typically used to perform cryptographic operations, such as hashing, encryption, and decryption, that are required for the operation of the blockchain.

---

## Security Model V2
**Security Model v2**: Hedera introduced the HSCS Security Model v2 to enhance the security of its network building with improved features to better safeguard against potential vulnerabilities and attacks.  By incorporating enhanced cryptographic techniques, robust consensus mechanisms, decentralized governance, and continuous monitoring.
Key features include - Restrictions on Smart contracts Storage; Prohibition on Delegate Calls to System Smart Contracts; Limited Access to EOAs' Storage; Token Allowance Requirement for Balance Changes

## Sidecars
**Sidecars**: In Services, a sidecar refers to additional records that are created alongside the main transaction records. These sidecar records provide more detailed information about the transactions, making it easier to debug and understand the state changes that occur as a result of the transactions.

## Synthetic Transactions
**Synthetic Transactions**: Internal transactions generated to maintain state, execute specific functions, or handle administrative tasks. These transactions are not initiated by users but are created and processed by the Hedera network itself. They ensure the proper functioning and upkeep of the network.

## System Contracts
**System Contracts**:  Smart contracts that have a permanent address in the network, meaning they will always be available at the same contract address. One example is the Token Service System Contract which allows smart contracts on Hedera to interact with the Hedera Token Service, offering functionalities like token creation, burning, and minting through the EVM

## System Transaction
**System Transaction**: A transaction created by the platform software to communicate system level information such as state signatures.

---

## Token Allowance
**Token Allowance**: A feature that allows an account (the owner) to authorize another account (the spender) to transfer tokens on its behalf up to a specified limit. This mechanism is similar to the ERC-20 allowance mechanism used in Ethereum.
The amount of tokens that a user has authorized a smart contract to spend on their behalf. This allowance is set by the user and can be modified or revoked at any time. It allows smart contracts to spend tokens on behalf of the user without requiring explicit approval for each transaction.

---

## Weibar
**WeiBar**: 1 hbar is equivalent to 100,000,000 (100 million) weibars. The unit of hbar used when you come in via an ethereum transaction - it's like wei is to ether.
