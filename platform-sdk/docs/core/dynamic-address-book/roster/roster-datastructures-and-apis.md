# Roster APIs

The following roster api is reduced from the address book to just the fields that are needed by the platform to establish mutual TLS connections, gossip, validate events and state, come to consensus, and detect an ISS.

The data for each node is contained in the node's `RosterEntry`.  

## Roster Interfaces

### RosterEntry 

```java
public interface RosterEntry extends SelfSerializable {
    public NodeId getNodeId();
    public long getWeight();
    public String getHostname();
    public int getPort();
    public PublicKey getSigningPublicKey();
    public x509Certificate getSigningCertificate();
    public boolean isZeroWeight();
}
```
### Roster

```java
public interface Roster extends Iterable<RosterEntry>, SelfSerializable{
    public RosterEntry getEntry(NodeId nodeId);
    public long getTotalWeight();
}
```