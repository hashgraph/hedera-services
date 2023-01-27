# Docker quickstart

To execute the integration and end-to-end tests of this repo a local hedera node need to be started.

We first discuss how to run a Hedera Services network on
your local machine with Docker Compose. This consists of [sourcing](#sourcing-the-image)
the image either from GCR or a local build, and then `docker-compose up`.

Next, we look in more detail at the `services-node` Docker image, covering
its usage and limitations.

### From GCR

Clone this repository:

```
git clone git@github.com:hashgraph/hedera-services.git
cd hedera-services
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
`/opt/hedera/services/.VERSION`. A reasonable tag is the output of
`git describe --tags --always --dirty`; for example,
`"oa-release-r5-rc6-13-gf18d2ff77-dirty"`. 
Ensure the Docker Compose .env file `(../docker/.env)` has an empty registry prefix and your tag:

```
TAG=oa-release-r5-rc6-13-gf18d2ff77-dirty
REGISTRY_PREFIX=
```

The file can be created by calling the `updateDockerEnv` gradle tasks (execute `./gradlew updateDockerEnv` from the root folder of the repo).

Third, build the image with an empty registry prefix and the `TAG` from your `.env` file by calling the `createDockerImage` gradle tasks (execute `./gradlew createDockerImage` from the root folder of the repo).

This is a multi-stage build that could take **several minutes**, depending on your environment.

## Starting the Compose network

The network can be started by calling the `startDockerContainers` gradle tasks (execute `./gradlew startDockerContainers` from the root folder of the repo).

The aggregated logs should end with lines such as:

```
...
node_2      | 2020-04-29 15:05:28.814 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#2.
node_0      | 2020-04-29 15:05:28.815 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#0.
node_1      | 2020-04-29 15:05:28.854 INFO  133  ServicesMain - Now current platform status = ACTIVE in HederaNode#1.
```

Notice that the Hedera Services and Platform logs for each node are externalized
under paths of the form _compose-network/node0/output/_.

During the initial startup, the network creates system accounts `0.0.1` through `0.0.100`.
It sets the key for each account to a `KeyList` of size one with a well-known Ed25519
keypair. The network reads the keypair in a legacy format from [here](../hedera-node/data/onboard/StartUpAccount.txt),
but the same keypair is available in PEM format using the PKCS8 encoding
[here](../hedera-node/data/onboard/devGenesisKeypair.pem) (the passphrase is `passphrase`).

Even more explicitly, the 32-byte hex-encoded private and public keys of the Ed25519 keypair are:

```
Public: 0aa8e21064c61eab86e2a9c164565b4e7a9a4146106e0a6cd03a8c395a110e92
Private: 91132178e72057a1d7528025956fe39b0b847f200ab59b2fdd367017f3087137
```

You can now run the integration and end-to-end tests. In general you can run any operation against your local network by using any HAPI client.

(This client uses account `0.0.2` as the default payer, and is aware of the above
keypair via its configuration in [_spec-default.properties_](../test-clients/src/main/resource/spec-default.properties)
under the `startupAccounts.path` key).

## Stopping or reinitializing the Compose network

As you run operations against the local network, each node will periodically save its state using
a combination of PostgreSQL tables under _compose-network/pgdata/_ and state files under, for example,
_compose-network/node0/saved/com.hedera.node.app.service.mono.ServicesMain/0/hedera/_.

To stop the network, call the `stopDockerContainers` gradle task (execute `./gradlew stopDockerContainers` from the root folder of the repo).

Given a clean shutdown of the containers, when you restart with the `startDockerContainers` gradle task,
the network will load from its last saved state. In general, for this to work correctly,
you should precede shutting down the network by submitting a `Freeze` transaction; e.g. via the
[`FreezeDockerNetwork`](../test-clients/src/main/java/com/hedera/services/bdd/suites/freeze/FreezeDockerNetwork.java)
client.

If you have a problem restarting the network after stopping, you can re-initialize it via:

```
docker-compose down
rm -rf compose-network/
```

## Understanding the Docker image

### Usage

In general, the `services-node` image will be run with three bind mounts---one to provide
the configuration and bootstrap assets; one to externalize the saved state data; and one to
externalize the Hedera Services and Platform logs. For example:

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
network defined by the [docker-compose.yml](../docker/docker-compose.yml) in this repository.
