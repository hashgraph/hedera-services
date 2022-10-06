# Configuration properties

There are five kinds of property-based configuration used by 
a Hedera Services network. These are,

1. **Bootstrap properties**, used only at the genesis of the network 
to create the initial system accounts and files. _Example_: the
`bootstrap.hapiPermissions.path`, which points to the initial 
contents of the HAPI permissions system file.
2. **Static properties**, used throughout the network's lifetime; 
but change rarely or never, requiring a network restart to do so. 
_Example_: `accounts.systemAdmin`, the number of an account granted 
[various privileges](./privileged-transactions.md).
3. **Dynamic properties**, also used for consensus processing, and
whose values can be changed during network operation by a `FileUpdate` to 
the file with number given by the static property 
`files.networkProperties`. _Example_: `tokens.maxPerAccount`, the
limit on how many HTS tokens an account may be associated with.
4. **Node properties**, which have no affect on consensus processing,
only customizing a given node's behavior. _Example_: `netty.prod.keepAliveTime`, the [keep-alive time](https://github.com/grpc/grpc/blob/master/doc/keepalive.md) for node's Netty server.
5. **OS Environment Variable**, which will override all other sources. These 
properties will behave as bootstrap properties in the sense that they will only be parsed 
at startup, and will also semi-behave like static properties in that a network restart
will be required to change them. _Example:_ `HEDERA_NODE_CONFIG='tokens.nfts.areEnabled=true files.maxSizeKb=4000'`   

Note that property-based configuration does not include the network's
address book, fee schedules, exchange rates, HAPI permissions, or
throttle definitions. 

# Property sources

The default values of all the network properties are in a 
[_bootstrap.properties_ file](../hedera-node/src/main/resources/bootstrap.properties) 
that is deployed with the software in 
a [JAR file](https://en.wikipedia.org/wiki/JAR_(file_format)). 
However, it is possible to use external configuration to override
each kind of properties as described below. 

## Overriding bootstrap and static properties

If a _data/config/bootstrap.properties_ file is present when the network
starts (including at genesis), then the contents of this file
override the defaults for any bootstrap or static property listed. 
For example, when starting a network for integration testing, the 
Services team often wants more permissive throttles than the 
production defaults; and uses a _data/config/bootstrap.properties_ of,
```
bootstrap.throttleDefsJson.resource=throttles-dev.json
```
This causes the throttling definitions file `0.0.123` to be initialized
with a [genesis state](../hedera-node/src/main/resources/throttles-dev.json) that allows higher tps.

Of course, if a _data/config/bootstrap.properties_ is used, it **must**
be present on all nodes! Otherwise the state would be inconsistent 
across the network. This makes the file less attractive for production 
use cases.

## Overriding dynamic properties

The only way to override dynamic properties is via the contents 
of the system file whose number is given by the static property 
`files.networkProperties` (`0.0.121` by default).

However, the genesis contents of this file are initialized from
the properties file referenced by bootstrap property 
`bootstrap.networkProperties.path`, which is 
_data/config/application.properties_ by default. When running 
locally, Services developers generally override 
`balances.exportDir.path`, which has a production default of 
_/opt/hgcapp/accountBalances_.

Please do keep in mind that setting _data/config/application.properties_
has **no impact after network genesis**! Once the network is 
initialized, the only way to override a dynmic property is by
a `FileUpdate` to file `0.0.121` with a payload of type 
[`ServicesConfigurationList`](https://hashgraph.github.io/hedera-protobufs/#proto.ServicesConfigurationList).

## Overriding node properties

If a _data/config/node.properties_ file is present whenever a node 
starts, then this file's contents override the defaults. For example,
when testing locally with multiple logical nodes in the same JVM, it 
is often useful to set the `hedera.profiles.active` node property 
to `DEV`, which by default causes only the node with account `0.0.3`
to bind its gRPC server.

## Overriding with `HEDERA_NODE_CONFIG`

Any property that can be set using a previous method can also be set
in the `HEDERA_NODE_CONFIG` operating system variable. As described above,
these properties are only parsed during application startup, and once
there cannot be changed until the application is restarted. **WARNING: this 
means that no properties expected to change during an application run should 
_ever_ be used in the environment variable!** Once set these properties will
take precedence over any of the same properties defined in other property
sources, and thus other property sources have no power to override the
environment variable properties at _any_ point in the app's lifecycle.

**`HEDERA_NODE_CONFIG` is the _only_ environment variable we currently support!**
Set _all_ properties in this variable as a whitespace-separated string of key-value
pairs of the form `key1=value1 key2=value2` (etc.). Example:
```
HEDERA_NODE_CONFIG='topics.maxNumber=10 grpc.port=50211 netty.mode=PROD'
```
Note that these key-value pairs will be parsed in the same way that properties 
files will be parsed, and thus the key-values should be sent in the same format. 
E.g. `fees.percentCongestionMultipliers`is a comma-separated list of strings, say 
`90,10x,95,25x,99,100x`; this property would be equivalently formatted in the 
environment variable:
```
HEDERA_NODE_CONFIG='fees.percentCongestionMultipliers=90,10x,95,25x,99,100x k2=v2'
```
Here's another example property, `tokens.nfts.mintThrottleScaleFactor`, with an 
example value of `5:2`:
```
HEDERA_NODE_CONFIG='tokens.nfts.mintThrottleScaleFactor=5:2 k2=v2 k3=v3' 
```
For properties that require spaces as part of the value, quotes can be used inside the environment 
variable string. Single- and double-quotes are interchangeable as long as each starting quote is
matched (balanced) by the same character at its end. E.g. `HEDERA_NODE_CONFIG='k1="path/to/my file/"'` and 
`HEDERA_NODE_CONFIG="k1='path/to/my file'"` are equivalent, but `HEDERA_NODE_CONFIG='k1='v1"'` 
is not equivalent (or even valid) because the second single-quote character matches the first single-quote 
character. Likewise, the quote characters surrounding the environment variable value can be either single- 
or double-quotes, i.e. `HEDERA_NODE_CONFIG='k1=v1 k2=v2'` is equivalent to `HEDERA_NODE_CONFIG="k1=v1 k2=v2"`
