# Hedera API (HAPI) Protobuf

The _\*.proto_ files in this repository define the services offered by a node in the Hedera public
network.

## Overview

There are five primary service families, which inter-operate on entities controlled by one (or more)
Ed25519 keypairs:

1. The
   [cryptocurrency service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/crypto_service.proto),
   for cryptocurrency accounts with transfers denominated in
   [hBar (ℏ)](https://help.hedera.com/hc/en-us/articles/360000674317-What-are-the-official-HBAR-cryptocurrency-denominations-).
2. The
   [consensus service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/consensus_service.proto),
   for fast and unbiased ordering of opaque binary messages exchanged on arbitrary topics.
3. The
   [smart contract service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/smart_contract_service.proto),
   for execution of Solidity contract creations and calls; contract may both possess ℏ themselves
   and exchange it with non-contract accounts.
4. The
   [file service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/file_service.proto),
   for storage and retrieval of opaque binary data.
5. The
   [token service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/token_service.proto),
   for token related operations such as create, update, mint, burn, transfer etc..

There are also three secondary service families:

1. The
   [network service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/network_service.proto),
   for operations scoped to the network or its constituent nodes rather user-controlled entities as
   above.
2. The
   [scheduling service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/schedule_service.proto),
   for scheduling a transaction to be executed when the ledger has received enough prequisite
   signatures.
3. The
   [freeze service](https://github.com/hashgraph/hedera-protobufs-java/blob/main/src/main/proto/freeze_service.proto),
   for use by privileged accounts to suspend network operations during a maintenance window.

It is important to note that most network services are gated by fees which must be paid **in ℏ from
a cryptocurrency account**. The payer authorizes a fee by signing an appropriate transaction with a
sufficient subset of the Ed25519/ECDSA(secp256k1) keys associated to their account.
