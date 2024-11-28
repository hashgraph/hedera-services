# Roster APIs

The following roster api is reduced from the address book to just the fields that are needed by the platform to establish mutual TLS connections, gossip, validate events and state, come to consensus, and detect an ISS.

The data for each node is contained in the node's `RosterEntry`.

## Roster Interfaces

### RosterEntry

```java
public interface RosterEntry extends SelfSerializable {
    NodeId getNodeId();
    long getWeight();
    String getHostname();
    int getPort();
    PublicKey getSigningPublicKey();
    X509Certificate getSigningCertificate();
    boolean isZeroWeight();
}
```

### Roster

```java
public interface Roster extends Iterable<RosterEntry>, SelfSerializable{
    int size();
    boolean contains(NodeId nodeId);
    RosterEntry getEntry(NodeId nodeId);
    long getTotalWeight();
}
```
