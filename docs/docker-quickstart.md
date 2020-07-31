# Docker quickstart

We first discuss how to run a Hedera Services network on 
your local machine with Docker Compose. This consists of [sourcing](#sourcing-the-image) 
the image either from GCR or a local build, and then `docker-compose up`.

Next, we look in more detail at the `services-node` Docker image, covering 
its usage and limitations.

## Sourcing the image

The `services-node` image can be [built locally](#building-locally) 
from the top-level directory in this repository, or [pulled](#from-gcr) from 
Google Container Registry (GCR). 

Prefer the latter until you want to test a change you have made to the source code.

### From GCR

Clone this repository:
```
git clone git@github.com:hashgraph/hedera-services.git
cd hedera-services
```

Ensure the Docker Compose [.env file](../.env) has the following contents:
```
TAG=0.6.0
REGISTRY_PREFIX=gcr.io/hedera-registry/
```

You can now [start the network](#starting-the-compose-network).

### Building locally

First, clone this repository:
```
git clone git@github.com:hashgraph/hedera-services.git
cd hedera-services
```

Second, choose a tag for your build. The tag will be added 
to the image as the contents of the file 
`/opt/hedera/services/.VERSION`.  A reasonable tag is the output of 
`git describe --tags --always --dirty`; for example, 
`"oa-release-r5-rc6-13-gf18d2ff77-dirty"`. Ensure the 
Docker Compose [.env file](../.env) has an empty registry prefix 
and your tag:
```
TAG=oa-release-r5-rc6-13-gf18d2ff77-dirty
REGISTRY_PREFIX=
```

Third, build the image:
```
docker-compose build
```
This is a multi-stage build that could take **several minutes**, 
depending on your environment. If you wish to use the `git describe` 
output as your tag, you might consider a script such as the 
[compose-build.sh](../compose-build.sh) in this repository to 
combine the second and third steps here.

## Starting the Compose network

Run:
```
docker-compose up
```

The aggregated logs should end with lines such as:
```
...
node_2      | 2020-04-29 15:05:28.814 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#2.
node_0      | 2020-04-29 15:05:28.815 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#0.
node_1      | 2020-04-29 15:05:28.854 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#1.
```

Notice that the Hedera Services and Swirlds Platform logs for each node are externalized 
under paths of the form _compose-network/node0/output/_. 

You can now run operations against your local network using any HAPI client. For example:
```
cd test-clients
../mvnw exec:java -Dexec.mainClass=com.hedera.services.bdd.suites.compose.LocalNetworkCheck -Dexec.cleanupDaemonThreads=false
```

## Stopping or reinitializing the Compose network

As you run operations against the local network, each node will periodically save its state using
a combination of PostgreSQL tables under _compose-network/pgdata/_ and state files under, for example,
_compose-network/node0/saved/com.hedera.services.ServicesMain/0/hedera/_.

To stop the network, use `Ctrl+C` (or `docker-compose stop` if running with detached containers).

Given a clean shutdown of the containers, when you restart with `docker-compose start`, 
the network will load from its last saved state. 

If an you have a problem restarting the network after stopping, you can simply re-initialize
it via:
```
docker-compose down
rm -rf compose-network
```

## Understanding the Docker image

### Usage

In general, the `services-node` image will be run with three bind mounts---one to provide
the configuration and bootstrap assets; one to externalize the saved state data; and one to
externalize the Hedera Services and Swirlds Platform logs. For example:

```
  docker run -d --name node0 \
    -v "${PATH_TO_CONFIG}:/opt/hedera/services/config-mount" \
    -v "${PATH_TO_STATE_DATA}:/opt/hedera/services/services/data/saved" \
    -v "${PATH_TO_LOGS}:/opt/hedera/services/services/output" \
    -p 50211:50211 \
    services-node
```

Note that the container must have routes to the other network nodes listed in 
_config.txt_, and the PostgreSQL server given by `dbConnection.host` in _settings.txt_. 

### Limitations

We created this image for development and proof-of-concept use cases. It does
not include features such as log management, health checks, metrics, 
config template support, key management, or resilient startup scripts; 
all of which would be desirable for a production use case. The image also 
serves the gRPC API only on port 50211, without TLS.

We suggest using the image only in enviroments such as the Docker Compose 
network defined by the [docker-compose.yml](../docker-compose.yml) in this repository.
