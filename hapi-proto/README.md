# Hedera API (HAPI) Protobuf

The _*.proto_ files in this repository define the services
offered by a node in the Hedera public network. 

## Overview 
There are four primary service families, which inter-operate on entities 
controlled by one (or more) Ed25519 keypairs:
1. The [cryptocurrency service](src/main/proto/CryptoService.proto),
for cryptocurrency accounts with transfers denominated 
in [hBar (ℏ)](https://help.hedera.com/hc/en-us/articles/360000674317-What-are-the-official-HBAR-cryptocurrency-denominations-).
2. The [consensus service](src/main/proto/ConsensusService.proto), for
fast and unbiased ordering of opaque binary messages exchanged on 
arbitrary topics.
3. The [smart contract service](src/main/proto/SmartContractService.proto), for
execution of Solidity contract creations and calls; contract may both possess
ℏ themselves and exchange it with non-contract accounts.
4. The [file service](src/main/proto/FileService.proto), for storage and 
retrieval of opaque binary data.
5. The [token service](src/main/proto/TokenService.proto), for token related operations such as create, update, mint, burn, transfer etc.. 

There are also two secondary service families:
1. The [network service](src/main/proto/NetworkService.proto), for operations scoped
to the network or its constituent nodes rather user-controlled entities as above.
2. The [freeze service](src/main/proto/FreezeService.proto), for use by 
privileged accounts to suspend network operations during a maintenance window.

It is important to note that most network services are gated by fees which 
must be paid **in ℏ from a cryptocurrency account**. The payer authorizes a
fee by signing an appropriate transaction with a sufficient subset of the 
Ed25519 keys associated to their account.
