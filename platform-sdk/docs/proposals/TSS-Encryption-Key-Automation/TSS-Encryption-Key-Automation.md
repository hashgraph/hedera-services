# TSS Encryption Key Automation

---

## Summary

In order to avoid burdening the Node Operators with maintenance of another encryption key, the life-cycle of the
`tssEncryptionKey` will be automated. 

| Metadata           | Entities      | 
|--------------------|---------------|
| Designers          | Edward        |
| Functional Impacts | Services      |
| Related Proposals  | TSS-Ledger-ID |
| HIPS               | None          |

---

## Purpose and Context

For more background and theory, please read the `TSS-Ledger-ID` and `TSS-Library` proposals. 

Each node must maintain a long-term BLS encryption key, called the `tssEncryptionKey`, for receiving its private TSS 
shares used in signing.  The public portion of this key must be available to every consensus node in the network 
that is participating in TSS signing. A node's public `tssEncryptionKey` is used to encrypt the private `shares of 
shares` intended for that node.  Aggregating the decrypted `shares of shares` allows the node to recover its private 
share keys.  

While the public key must be known by all nodes, the private `tssEncryptionKey` must not be known by any other node. 
The security of the ledger id and the ledger's private signing key is dependent on the private `tssEncryptionKey` 
remaining private to each node.  If a single party were to obtain enough of the private `tssEncryptionKeys`, they 
would be able to recover the private shares of multiple nodes and aggregate a threshold number of private shares to 
recover the ledger's private signing key.

The reason that the `tssEncryptionKey` must be maintained long-term is that the public key must be known by other 
nodes before they can generate private shares for the node. If the `tssEncryptionKey` were to change, the node would 
not be able to participate in TSS signing until the next candidate roster is keyed and adopted.   

To minimize the number of node operator touch points, the life-cycle of the `tssEncryptionKey` should be automated.


### Dependencies, Interactions, and Implications

This proposal should be delivered with or before the delivery of `TSS-Ledger-ID`, which will attempt to key a candidate 
roster nightly.  The `TSS-Ledger-ID` capability cannot be turned out all nodes knowing the public `tssEncryptionKey` 
of all nodes. 

### Requirements

1. The public `tssEncryptionKey` of a node must be known by all nodes. 
2. The private `tssEncryptionKey` of a node must be kept private to that node.
3. The lifecycle of the `tssEncryptionKey` should be automated.

### Design Decisions

* The private `tssEncryptionKey` will be stored in the same directory as the node's private gossip signing key.
* The public `tssEncryptionKey` will be stored in the `TssBaseService` state. 
* A new `TssEncryptionKeyTransaction` will be, gossiped, come to consensus, and handled by the `TssBaseService` to 
  update the public `tssEncryptionKey` in the state.

#### Alternatives Considered

The alternative to the automated lifecycle is to have the node operators manually generate the `tssEncryptionKey` 
before the node starts and provide the public key to the network through a transaction signed by the node admin key. 

Requiring human intervention for this key increases friction that can easily be avoided.  This saves all the testing 
labor related to testing human workflows.   

---

## Changes

The changes to the system will be purely additive. 

### Architecture and/or Components

The `TssBaseService` already exists with its own state.  
* This proposal adds a new `Map<Long, Bytes>` to the state for storing the public key of each node.  

The `KeysAndCerts.java` record is extended with `byte[] privateTssEncryptionKey` and `byte[] publicTssEncryptionKey`. 

### Core Behaviors

There are two new behaviors that will be added to the system. 
1. At `System Startup`, during cryptography loading, the `tssEncryptionKey` will be loaded from disk or generated if it 
   does not exist.
2. At the start of `TssBaseService` execution, the `TssBaseService` will verify that the public `tssEncryptionKey` 
   is in the state, or send a transaction to update the public key if it is not.

#### Loading Cryptography

In the `EnhancedKeystoreLoader`: 
1. During the `scan()` phase, the private `tssEncryptionKey` will be loaded from disk if present.
2. During the `generate()` phase, if the private `tssEncryptionKey` is not present: 
   1. Create a new public/private key pair.
   2. Write the private key to disk. 
   
At the end of loading cryptography, the `KeysAndCerts` record will be in one of two states: 
1. The private `tssEncryptionKey` is loaded from disk and there is no public `tssEncryptionKey`. 
2. The private and public `tssEncryptionKey` were generated, are present, and match each other. 

#### TssBaseService Initialization

 At the initialization of the `TssBaseService` the loaded public/private `tssEncryptionKey` will be provided.  The 
 private key will always be provided, but the public key may be null.  There are three scenarios: 

1. The public `tssEncryptionKey` is provided indicating a new key-pair was generated.  
   1. Once execution is live, a `TssEncryptionKeyTransaction` will need to be sent periodically until the state 
      reflects the correct public key. 
2. The public `tssEncryptionKey` is not provided indicating only the private key was loaded from disk. 
   1. If there is no public key in the state, or the public key in the state does not matching the loaded 
      private key, a new public/private `tssEncryptionKey` will need to be generated through a call back to the 
      cryptography loader to generate the key and persist the new private key to disk.  The new public key will need 
      to be distributed to the rest of the network as in `1.1` above.
   2. If the public key in the state matches the loaded private key, all is good.

At the end of the `TssBaseService` initialization there will be two outcomes: 
1. The public key in the state matches the private key and the `TssBaseService` is ready to execute normally. 
2. At the start of `TssBaseService` execution, it needs to updated the public key on the network and cannot perform 
   its normal execution until the public key has been gossiped, handled, and updated in the state.  

#### TssBaseService Start Of Execution

If there is a public key update needing to happen: 
1. Suspend normal execution: The `TssBaseService` will not be able to decrypt its private shares and cannot 
   participate in signing or re-keying.
2. Send (and periodically resend) a `TssEncryptionKeyTransaction` until the public key is updated in the state.
3. Normal execution of the `TssBaseService` cannot resume until a new candidate roster has been keyed and adopted 
   that provides private shares to this node that can be decrypted using the new `tssEncryptionKey`.

New `TssEncryptionKeyTransaction` handle workflow: 
* Upon receipt of a `TssEncryptionKeyTransaction`:
  1. Add the public key to the `TssBaseService` state.
  2. Disable any mechanism created for resending the transaction. 
  3. Check condition for resuming normal execution.

Resuming / Proceeding with Normal Execution: 
* Normal execution can be resumed when private shares can be decrypted from the `TssMessages` belonging to the 
  lasted `active roster`. 

### Public API

#### TssEncryptionKeyTransaction Protobuf

The `TssEncryptionKeyTransaction` will only appear in events created from the node sending the transaction.  The 
signature on the event and the creator identifier of the event are sufficient to provide authenticity of the 
transaction.  

```protobuf
message TssEncryptionKeyTransaction {
  /**
   * The raw bytes of the public TSS encryption key of the node sending the transaction. 
   */
  bytes publicTssEncryptionKey = 1;
}
```

#### TssBaseService New State Protobuf

The `TssBaseService` state will be extended with a new map of the public `tssEncryptionKey` for each node. 
1. The keys of the map are Longs/UInt64 for the `nodeId` of the nodes
2. The values of the latest `TssEncryptionKeyTransaction` from each node. 

Data Lifecycle: 
* When a node id is not present in any of the `active rosters` or a `candidate roster`, the entry for that node id 
  should be removed.   This cleanup task should trigger any time there is a candidate or active roster state change. 

### Configuration

No new configuration is needed. 

### Metrics

A health metric should be created to indicate the state the `TssBaseService` is in: 
1. distributing the public `tssEncryptionKey` to the network.
2. public `tssEncryptionKey` is good, waiting for the next candidate roster to be keyed and adopted.
3. Able to participate in signing with the active roster. (Private shares are decryptable with current 
   `tssEncryptionKey`.)

A health metric should be created that indicates the number of active shares available for signing.  Nodes which are 
off line or have bad status in the above health metric should not have their shares counted towards the available 
active shares for signing.  A warning should be generated if the number of active shares drops close to the 
aggregation threshold required to create a ledger signature.   A critical error should be generated if the number of 
available shares drops below the aggregation threshold.


### Performance

There are no expected performance impacts from this proposal. 

---

## Test Plan

The behavior of this proposal is fundamental to the operation of TSS.   It is sufficient that the TSS capability 
comes live with existing TSS tests once this proposal is deliverable.  

---

## Implementation and Delivery Plan

The implementation of this capability is able to proceed now.  

Once delivered, it is advisable that Node Operators backup the cryptography directory containing the 
`tssEncryptionKey` private key and include the private key in their disaster recovery plan.   If this is not done, 
then restoring the node from a backup will require the node to generate a new `tssEncryptionKey` and distribute the
public key to the network.   This will prevent the node from being able to participate in signing until the next 
candidate roster is adopted. 