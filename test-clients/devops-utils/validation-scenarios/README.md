# Usage examples

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

This will overwrite the _config.yml_ with the id numbers of the various **persistent** entities
created during the test. For example,
```
...
  localhost:
    bootstrap: 2
    nodes:
    - {account: 3, ipv4Addr: 127.0.0.1}
    scenarios:
      consensus: {persistent: 1019}
      contract:
        persistent: {bytecode: 1016, luckyNo: 42, num: 1017, source: Multipurpose.sol}
      crypto: {receiver: 1012, sender: 1011}
      file:
        persistent: {contents: MrBleaney.txt, num: 1014}
```

## Checking the payer balance on mainnet

Given the _config.yml_ in this directory, and an appropriate _keys/mainnet-account950.pem_ 
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
