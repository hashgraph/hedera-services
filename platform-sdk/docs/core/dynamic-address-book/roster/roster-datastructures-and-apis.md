# Roster APIs

The following roster api is reduced from the address book to just the fields that are needed by the platform to establish mutual TLS connections, gossip, validate events and state, come to consensus, and detect an ISS.

The data for each node is contained in the node's `RosterEntry`.

The `Internal` and `External` hostname and port are for detecting inbound and output gossip connections.  A Node will receive connections on the `internal` endpoint and create outbound connections to other node's `external` endpoints.  This difference is to account for network routing and containerization.  

## Roster Interfaces

### RosterEntry 

```java
public interface RosterEntry extends SelfSerializable {
    public NodeId getNodeId();
    public long getWeight();
    public String getInternalHostname();
    public int getInternalPort();
    public String getExternalHostname();
    public int getExternalPort();
    public PublicKey getSigningPublicKey();
    public x509Certificate getSigningCertificate();
    public MutableRosterEntry copy();
    public RosterEntry seal();
    public boolean isZeroWeight();
}
```

```java
public interface MutableRosterEntry extends RosterEntry {
    public void setNodeId(NodeId nodeId);
    public void setWeight(long weight);
    public void setInternalHostname(String hostname);
    public void setInternalPort(int port);
    public void setExternalHostname(String hostname);
    public void setExternalPort(int port);
    public void setSigningCertificate(x509Certificate signingCert);
}
```

### Roster

```java
public interface Roster extends Iterable<RosterEntry>, SelfSerializable{
    public RosterEntry getEntry(NodeId nodeId);
    public long getTotalWeight();
    public MutableRoster copy();
    public Roster seal();
}
```

```java
public interface MutableRoster extends Roster {
    public void addEntry(RosterEntry entry);
    public void removeEntry(NodeId nodeId);
}
```