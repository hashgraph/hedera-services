## Exact-weight TSS

A **threshold signature scheme (TSS)** allows a group of parties to collectively sign a message by aggregating their
partial signatures. The **access structure** of a TSS is the set of subsets of the parties whose partial signatures
can be combined to form a valid signature. For an aBFT proof-of-stake blockchain, the ideal access structure at any
point in the protocol includes any set of nodes holding at least 1/3 of the total stake. This means the TSS must support
arbitrarily precise (in the case of Hiero, 64-bit) party weights.

Ignoring the amount of work and party interactions required of a verifier, it is relatively straightforward to design
such a TSS. It is enormously harder to design an **efficient** TSS with this property. Efficiency demands,
- A stable, succinct **ledger identity** that verifiers can trust during any period since adoption of the TSS in which
the network's aBFT properties are not compromised; no matter how many nodes are added or removed, or change their
signing keys.
- **Concise signatures** whose length is independent of the size of the network.
- **Fixed-cost, non-interactive verification** that is again independent of the size of the network.

Hiero uses an efficient exact-weight TSS where signing works via the **hinTS** scheme published in [1], and the
ledger's identity is the hash of the ids, weights, and Schnorr public keys of the permissioned nodes in the network at
the time the TSS was adopted.

This document provides the high-level design of how Hiero TSS is implemented.

### Relation to the `RosterService` lifecycle

Since the very identity of the ledger in Hiero TSS depends on the ids, weights, and Schnorr keys of the nodes in
the network roster at the time of adoption, the TSS implementation is naturally guided by the lifecycle of the

`RosterService`. From the TSS perspective, this lifecycle has three distinct phases:
1. **Bootstrap**: Only a genesis roster exists, and although the nodes have secure communication channels,
there is no consensus state with the Schnorr or hinTS public keys of the nodes. In this phase, the TSS system must
derive this state from gossip, complete preprocessing for the hinTS scheme, and prove the initial hinTS verification
key and fully active roster were derived from the ledger id using Schnorr signatures from nodes with at least 1/3 of
the weight in the genesis roster.
2. **Transition**: The current roster is fully active; the TSS state includes a consensus proof that its hinTS
verification key was derived from the ledger id. There is also a candidate roster that reflects the latest dynamic
address book changes and HBAR stake adjustments. In this phase, the TSS system must repeat hinTS preprocessing for
the candidate roster; and collect enough Schnorr signatures from the current roster to (recursively) prove the new
hinTS verification key was also derived from the ledger id.
3. **Handoff**: The current roster is fully active, but there is no candidate roster because the network has just
replaced its current roster with the candidate roster. In this phase, the TSS system may, for example, purge any
obsolete state accumulated during its previous transition phases; but it has no urgent responsibilities besides, of
course, providing ledger signatures.

### The `RosterCompanionService` abstraction

We structure the TSS system as two services with distinct responsibilities:
1. The `HintsService`, which implements the six main hinTS algorithms that function given the **common reference
string (CRS)** grounding the protocol; that is, preprocessing, key generation, hint generation, partial signing,
partial verification, and signature aggregation.
2. The `HistoryService`, which is unaware of its role in specifically TSS, and implements a more general function of
proving via recursive SNARK that a set of node ids, weights, Schnorr keys; and an arbitrary binary string, were derived
from the ledger id with each transition in the derivation having valid Schnorr signatures from nodes with at least 1/3
of the weight in the source roster.

The node software combines these services to achieve TSS by setting the binary strings in the `HistoryService` proofs
to the verification keys computed by the `HintsService` for the corresponding rosters.

Despite their separate responsibilities, both the `HintsService` and `HistoryService` share a high-level design that
we call the `RosterCompanionService`. This abstraction is a service whose goal is to derive some **primary state** for
each roster used by the `RosterService`, and that progresses, round by round, toward this goal by a **reconciliation
loop** driven by the `HandleWorkflow`. It uses whatever **secondary state** it needs and gossips any
**node transactions** required to implement this reconciliation loop.

A `RosterCompanionService` is **ready** when it has derived its primary state for the current roster. Becoming ready is
the prime objective of any `RosterCompanionService` during the bootstrap phase.

![RCS bootstrap schematic](assets/rcs-bootstrap-schematic.png)

Completing bootstrap is equivalent to starting in a handoff phase, where a `RosterCompanionService` has no primary
state to derive. Its only required function is to do **roster-scoped work** for the current roster on behalf of other
protocol infrastructure components; though it may also use the reconciliation loop to purge obsolete state.

![RCS handoff schematic](assets/rcs-handoff-schematic.png)

At distinguished points (e.g., at a stake period boundary), the Hiero protocol creates a candidate roster that reflects
dynamic address book changes and HBAR stake adjustments. This initiates a transition phase, where the
`RosterCompanionService` must use the reconciliation loop to derive its primary state for the candidate roster, at the
same time it is doing tasks scoped to the current roster.

![RCS transition schematic](assets/rcs-transition-schematic.png)

### The `HintsService` as a `RosterCompanionService`

We first map the `HintsService` to the `RosterCompanionService` abstraction. At a high level,
- The **primary state** of the `HintsService` consists of a roster-scoped hinTS construction. Each includes a source
roster hash; a target roster hash; the target roster mapping from node ids to hinTS party ids; a consensus time for
the next aggregation attempt (if the construction is waiting on hinTS keys); the consensus time of the final
aggregation time (if the construction is waiting for votes on preprocessing outputs); and finally, if the construction
is complete, the hinTS aggregation and verification keys for the construction. To simplify connecting secondary state
with primary state, each construction may also have a unique numeric id.
- The **secondary state** of the `HintsService` is everything needed to facilitate deterministic progress on a
construction; in particular, for nodes that reconnect during the construction. This likely includes,
1. _Per construction size $M = 2^k$_ : For as many parties as possible, for party with id $i \in [0, M)$, the party's
hinTS key; the node id that submitted that hinTS key; the consensus time the hinTS key was adopted in the ongoing
construction; and, if applicable, a revised hinTS key the same node wishes to use in subsequent constructions of
size $M$. (Note that a node's assigned party id for a particular construction size never changes; so this implies
such secondary state is fully purged before reusing a party id for a new node id.)
2. _Per construction id $c$_ : For a subset of node ids $\{ i_1, \ldots, i_n \}$ in the source roster of
construction $c$ accounting for at least 1/3 of its weight, their consensus vote for a particular preprocessing
output with aggregation and verification keys for construction $c$.
- The **reconciliation loop** of the `HintsService` evolves this secondary state by a combination of scheduling
expensive cryptographic operations to run off the `handleTransaction` thread, and gossiping the results of these
operations (or votes on those results) to other nodes. These node operations likely include,
1. `HintsKeyPublication` - a transaction publishing the node's hinTS key for a particular construction size for
use in the next construction of that size (or an ongoing construction of that size, if this is the first such
publication for the node).
2. `HintsAggregationVote` - a transaction publishing the node's vote for a particular preprocessing output for
a certain construction id.
- The `HintsService` is **ready** when it has completed a hinTS construction for the current roster.
- The **roster-scoped work** of the `HintsService` is to accept a message (generally a block hash) and return a
future that resolves to the hinTS signature on the message. This will require gossiping partial signatures via
a `HintsPartialSignature` node transaction so the node can run the hinTS aggregation algorithm to produce a succinct
signature for a set of partial signatures whose parties are nodes with at least 1/3 weight in the current roster.

### The `HistoryService` as a `RosterCompanionService`

Next we map the `HistoryService` to the `RosterCompanionService` abstraction. At a high level,
- The **primary state** of the `HistoryService` includes both the ledger id, and a roster-scoped construction of a
proof that certain metadata and roster were derived from the ledger id. Each construction includes a source roster hash;
a target roster hash; a proof that the source roster derived from the ledger id; a consensus time for the next attempt
to assemble the Schnorr keys for the target roster (if the construction is waiting on Schnorr keys); the consensus final
assembly time for the Schnorr keys; and finally, if the construction is complete, the proof that the target roster and
metadata are derived from the ledger id.
- The **secondary state** of the `HistoryService` is everything needed to facilitate deterministic progress on a
construction; in particular, for nodes that reconnect during the construction. This likely includes,
1. _Per node id $i$_ : The node's Schnorr key; the node's consensus time for the Schnorr key; and, if applicable, a
revised Schnorr key the same node wishes to use in subsequent constructions.
2. _Per construction id $c$_ : For a subset of node ids $\{ i_1, \ldots, i_n \}$ in the source roster of construction
$c$ accounting for at least 1/3 of its weight, their signatures on a particular metadata and roster derivation for
construction $c$.
3. _Per construction id $c$_ : For a subset of node ids $\{ i_1, \ldots, i_n \}$ in the source roster of construction
$c$ accounting for at least 1/3 of its weight, their consensus vote for a particular proof output for construction $c$.
- The **reconciliation loop** of the `HistoryService` evolves this secondary state by a combination of scheduling
expensive cryptographic operations to run off the `handleTransaction` thread, and gossiping the results of these
operations (or votes on those results) to other nodes. These node operations likely include,
1. `HistoryProofKeyPublication` - a transaction publishing the node's Schnorr key for use in the next construction.
2. `HistoryAssemblySignature` - a transaction publishing the node's signature on a particular metadata and roster
assembly for a certain construction id.
3. `HistoryProofVote` - a transaction publishing the node's vote for a particular proof output for a certain
construction id.
- The `HistoryService` is **ready** when it has completed a proof that the current roster and metadata were derived from
the ledger id.
- The **roster-scoped work** of the `HistoryService` is to accept a byte string which must match the metadata of the
current roster, and return the proof that this metadata and the current roster were derived from the ledger id.

### Integration with protocol components

The TSS system is then just the combination of the `HintsService` and `HistoryService` with the `RosterService`, with
the `HistoryService` metadata always set to the verification key of the `HintsService` for the current roster.

There are only a few other details; namely,
- The `HandleWorkflow` is responsible for driving the reconciliation loops of both companion services.
- The `IngestWorkflow` must reject user transactions during the bootstrap phase.
- The `BlockStreamManager` must include every round in the genesis block until the TSS system is ready. (And thus it
must be possible to tell the platform not to use any of these rounds for state saving or reconnect.)

## References

1. Garg, S., Jain, A., Mukherjee, P., Sinha, R., Wang, M., & Zhang, Y. (2023). *hinTS: Threshold Signatures with Silent
   Setup*. Cryptology ePrint Archive, Paper 2023/567. Retrieved from [https://eprint.iacr.org/2023/567](https://eprint.iacr.org/2023/567)
