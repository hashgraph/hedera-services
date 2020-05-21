# Overview

_ValidationScenarios.jar_ is an executable JAR that can run one or more scenarios
against a **target network** using a **bootstrap** account to fund a `scenarioPayer` 
that performs (most of) these operations.

All provided network nodes are targeted in a round-robin fashion.

The scenario names are `crypto`, `file`, `contract`, and `consensus`. Each 
scenario can be limited to acting against **persistent** entities that were already 
created by a previous execution; or the scenarios can be allowed to also create 
and delete **novel** entites during execution.

If all scenarios are run with the `novel` flag, then at least one of every type
of HAPI operation is performed against the target network.

This looks like:
```
BOOTSTRAP_PASSPHRASE=secret \
  java -jar ValidationScenarios.jar \
    "target=mainnet" \ 
    "scenarios=file,contract" \
    "novel=true" 
```
More example usages are given below.

## Target networks and bootstrap accounts

Target networks are defined via lists of nodes in the [_config.yml_](./config.yml)

Each target network must have a bootstrap account with a single key; the key must
be present in PEM format under the _keys/_ directory with the naming convention 
_\{targetNetwork\}-account\{bootstrapNum\}.pem_. **IMPORTANT**: If the PEM passphrase 
is not the string "swirlds", it must be set in the `BOOTSTRAP_PASSPHRASE` environment 
variable.

As an example, take this mapping in _config.yml_:
```
networks:
  testnet:
    bootstrap: 50
    nodes:
    - {account: 3, ipv4Addr: 34.94.106.61}
    - {account: 4, ipv4Addr: 35.237.119.55}
    - {account: 5, ipv4Addr: 35.245.27.193}
    - {account: 6, ipv4Addr: 34.83.112.116}
```
It is necessary to save the key for account `0.0.50` in PEM format as 
_keys/testnet-account50.pem_ file, and to export its passphrase to `BOOTSTRAP_PASSPHRASE` 
in the environment used to execute the JAR.

## Persistent entities

The first time scenarios are run against a given target network, 
various persistent entities are created, including a `scenarioPayer` 
whose balance is topped up to be at least `ensureScenarioPayerHbars` at 
the beginning of the execution. 

The _config.yml_ is then overwritten with certain metadata for these persistent entities.
For example, since the JAR has already been executed against `testnet`, we have:
```
...
    scenarioPayer: 49542
    scenarios:
      consensus: {persistent: 39045}
      contract:
        persistent: {bytecode: 39042, luckyNo: 42, num: 39043, source: Multipurpose.sol}
      crypto: {receiver: 39038, sender: 39037}
      file:
        persistent: {contents: MrBleaney.txt, num: 39040}
```

**IMPORTANT:** If you delete any of this metadata from the _config.yml_, new 
persistent entities will be recreated upon the next execution of the JAR. This
could be necessary if, for example, the key files controlling these entities
were accidentally deleted.

# Usage examples

## Checking the bootstrap account balance on mainnet

If no scenarios are given, then executing the JAR simply reports the bootstrap account balance. 
For example, given the _config.yml_ in this directory, and an appropriate _keys/mainnet-account950.pem_ 
with passphrase exported in the `BOOTSTRAP_PASSPHRASE` environment variable, 

```
$ java -jar ValidationScenarios.jar target=mainnet 
2020-03-31 00:34:21.589 INFO   133  ValidationScenarios - Using nodes 35.237.200.180:0.0.3,35.186.191.247:0.0.4,35.192.2.25:0.0.5,35.199.161.108:0.0.6,35.203.82.240:0.0.7,35.236.5.219:0.0.8,35.197.192.225:0.0.9,35.242.233.154:0.0.10,35.240.118.96:0.0.11,35.204.86.32:0.0.12,35.234.132.107:0.0.13,35.236.2.27:0.0.14,35.228.11.53:0.0.15
2020-03-31 00:34:21.591 INFO   100  ValidationScenarios - -------------- STARTING ValidationScenarios SUITE --------------
...
2020-03-31 00:34:22.620 INFO   130  ValidationScenarios - -------------- RESULTS OF ValidationScenarios SUITE --------------
2020-03-31 00:34:22.620 INFO   132  ValidationScenarios - Spec{name=RecordPayerBalance, status=PASSED}
2020-03-31 00:34:22.621 INFO   1031 ValidationScenarios - ------------------------------------------------------------------
2020-03-31 00:34:22.622 INFO   1022 ValidationScenarios - 0.0.950 balance is now 48774486546 tinyBars (487.74 ħ)
```

## Rerunning a read-only file scenario against testnet 

Given the _config.yml_ in this directory, the included _keys/testnet-account982.pem_, and
the included _files/MrBleaney.txt_, to validate the persistent file has the expected contents,

```
$ java -jar ValidationScenarios.jar target=testnet scenarios=file novel=false
2020-03-31 00:54:20.740 INFO   133  ValidationScenarios - Using nodes 35.188.20.11:0.0.3,35.224.154.10:0.0.4,34.66.20.182:0.0.5,35.238.127.7:0.0.6
2020-03-31 00:54:20.742 INFO   100  ValidationScenarios - -------------- STARTING ValidationScenarios SUITE --------------
...
2020-03-31 00:54:22.271 INFO   130  ValidationScenarios - -------------- RESULTS OF ValidationScenarios SUITE --------------
2020-03-31 00:54:22.271 INFO   132  ValidationScenarios - Spec{name=RecordPayerBalance, status=PASSED}
2020-03-31 00:54:22.271 INFO   132  ValidationScenarios - Spec{name=FileScenario, status=PASSED}
2020-03-31 00:54:22.271 INFO   132  ValidationScenarios - Spec{name=RecordPayerBalance, status=PASSED}
2020-03-31 00:54:22.272 INFO   1027 ValidationScenarios - ------------------------------------------------------------------
2020-03-31 00:54:22.274 INFO   1012 ValidationScenarios - 0.0.982 balance change was -1589022 tinyBars (-0.02 ħ)
```

## Running all validation scenarios against a local network

Given the _config.yml_ in this directory, and the included _keys/localhost-account2.pem_,

```
$ java -jar ValidationScenarios.jar target=localhost scenarios=crypto,file,contract,consensus
...
2020-03-31 00:45:53.471 INFO   130  ValidationScenarios - -------------- RESULTS OF ValidationScenarios SUITE --------------
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=RecordPayerBalance, status=PASSED}
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=CryptoScenario, status=PASSED}
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=FileScenario, status=PASSED}
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=ContractScenario, status=PASSED}
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=ConsensusScenario, status=PASSED}
2020-03-31 00:45:53.472 INFO   132  ValidationScenarios - Spec{name=RecordPayerBalance, status=PASSED}
2020-03-31 00:45:53.473 INFO   1031 ValidationScenarios - ------------------------------------------------------------------
2020-03-31 00:45:53.473 INFO   1033 ValidationScenarios - Novel account used (should now be deleted) was 0.0.1013
2020-03-31 00:45:53.473 INFO   1035 ValidationScenarios - Novel file used (should now be deleted) was 0.0.1015
2020-03-31 00:45:53.474 INFO   1037 ValidationScenarios - Novel contract used (should now be deleted) was 0.0.1018
2020-03-31 00:45:53.474 INFO   1039 ValidationScenarios - Novel topic used (should now be deleted) was 0.0.1020
2020-03-31 00:45:53.475 INFO   1016 ValidationScenarios - 0.0.2 balance change was -3351962661 tinyBars (-33.52 ħ)
```
