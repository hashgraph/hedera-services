### Workflow Onset

At the beginning of each workflow, a `Transaction` has to be parsed and validated.
As these steps are the same for all workflows, this functionality has been extracted and made available independently.
All related classes can be found in the package `com.hedera.node.app.workflows.onset`.
Details about the required pre-checks can be found [here](transaction-prechecks.md).

### Ingest Workflow

The package `com.hedera.node.app.workflows.ingest` contains the ingest workflow. A rough overview can be seen in the diagram below.

![Diagram of ingest workflow](images/Ingest%20Workflow.png)

When a new message arrives at the HAPI-endpoint, the byte-buffer that contains the transaction is sent to the ingest workflow.
The gRPC-server is responsible for Thread-Management.
The ingest-workflow is single-threaded, but multiple calls can run in parallel.

The ingest workflow consists of the following steps:

1. **Parse transaction.** The transaction arrives as a byte-array. The required parts are parsed and the structure and syntax are validated.
2. **Check semantics.** The semantics of the transaction are validated. This check is specific to each type of transaction.
3. **Get payer's account.** The account data of the payer is read from the latest immutable state.
4. **Check payer's signature.** The signature of the payer is checked. (Please note: other signatures are not checked here, but in later stages)
5. **Check account balance.** The account of the payer is checked to ensure it is able to pay the fee.
6. **Check throttles.** Throttling must be observed.
7. **Submit to platform.** The transaction is submitted to the platform for further processing.
8. **TransactionResponse.** Return `TransactionResponse`  with result-code.

If all checks have been successful, the transaction has been submitted to the platform and the precheck-code of the returned `TransactionResponse` is `OK`.
Otherwise the transaction is rejected with an appropriate response code.
In case of insufficient funds, the returned `TransactionResponse` also contains an estimation of the required fee.