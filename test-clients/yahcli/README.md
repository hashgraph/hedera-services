# Yahcli v0.1.0
Yahcli (Yet Another Hedera Command Line Interface) is able to perform the 
listed actions against a specified network.

1. Account Operations
    - Get Balance.
2. System File Operations
    - Download all/specific file.
    - Upload a System file.
3. Fees Operations
    - List basic transaction and query fees of all/specific service.

# Setting up the working directory 

Yahcli needs the key for a "default" payer to use for each network. 
To specify a key for account `0.0.2` on previewnet, you would create
a directory structure as below:
```
├── config.yml
├── previewnet
│   └── keys
│       ├── account2.pass
│       └── account2.pem
```

Where the _config.yml_ would have contents like:
```
defaultNetwork: previewnet

networks:
  previewnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 35.231.208.148 }
```

You can add details for multiple networks to this config file; for example,
you could add information about stabletestnet.
```
defaultNetwork: previewnet

networks:
  previewnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 35.231.208.148 }
  stabletestnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 34.94.106.61 }
      - { id: 1, account: 4, ipv4Addr: 35.237.119.55 }
```

For each network you add, there needs to be a _{network}/keys/_ 
folder should contain `account{num}.pem` and `account{num}.pass` 
pair for each account that you want to use with that network, where  
_num_ is the account number and `account{num}.pass` contains 
the passphrase for the `account{num}.pem` file (or is empty if 
there is no passphrase for the PEM).

**IMPORTANT:** Support for multisig accounts is not yet implemented.

# Running commands

To list all available commands, we run:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 help
Usage: yahcli [-c=config YAML] [-f=fee] [-n=network] [-p=payer] [COMMAND]
Perform operations against well-known entities on a Hedera Services network
  -c, --config=config YAML
  -f, --fixed-fee=fee
  -n, --network=network
  -p, --payer=payer
Commands:
  help      Displays help information about the specified command
  accounts  Perform account operations
  sysfiles  Perform system file operations
  fees      Perform system fee operations
``` 
:information_desk_person: Since we only have a key for account `0.0.2` on previewnet, 
we will need to use `-p 2` for the payer argument when running against this network.

To download the fee schedules from previewnet given the config above, we would run:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -p 2 -n previewnet sysfiles download fees
Targeting previewnet, paying with 0.0.2
Downloading the fees...OK
$ ls previewnet/sysfiles/
feeSchedules.json
```
:turtle: The docker image needs to launch a JAR, which is fairly slow. This will take a few seconds to run.

The fee schedules were downloaded in JSON form to _previewnet/sysfiles/feeSchedules.json_, which is the 
default location. To see more options for the `download` subcommand, we can run:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0  sysfiles download help
Usage: yahcli sysfiles download [-d=destination directory] <sysfiles>...
                                [COMMAND]
Download system files
      <sysfiles>...   one or more from { address-book, node-details, fees,
                        rates, props, permissions, throttles } (or { 101, 102,
                        111, 112, 121, 122, 123 })---or 'all'
  -d, --dest-dir=destination directory
```

## Getting account balances
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -n previewnet -p 2 accounts balance 56 50
Targeting previewnet, paying with 0.0.2
---------------------|----------------------|
          Account Id |              Balance |
---------------------|----------------------|

              0.0.56 |                    0 |
              0.0.50 |          15000000000 |
```

## Updating the address book and/or node details
For this example, we will run against a `localhost` network since we will modify a system file.

We want to add an address book entry for a new node with `nodeId=3`. The DER-encoded RSA public 
key of the node is in a file _node3.der_, and its TLS X509 cert is in a file _node3.crt_. We place 
these files in the directory structure below.
```
localhost
├── keys
│   ├── account2.pass
│   ├── account2.pem
│   ├── account55.pass
│   └── account55.pem
└── sysfiles
    ├── certs
    │   └── node3.crt
    └── pubkeys
        └── node3.der
```

We first download the existing address book,
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -n localhost -p 2 sysfiles download address-book
Targeting localhost, paying with 0.0.2
Downloading the address-book...OK
```

Next we edit the newly-downloaded _localhost/sysfiles/addressBook.json_ and 
add a new entry with `nodeId=3`, as below.

:information_desk_person: By using the `!` character in the `certHash` and `rsaPubKey` fields,
we tell yahcli to automatically compute their values from the _certs/node3.crt_ and _pubkeys/node3.der_
files, respectively.
```
...
  }, {
    "deprecatedIp" : "127.0.0.1",
    "deprecatedMemo" : "0.0.6",
    "deprecatedPortNo" : 50207,
    "nodeId" : 3,
    "certHash" : "!",
    "rsaPubKey" : "!",
    "nodeAccount" : "0.0.6",
    "endpoints" : [ {
      "ipAddressV4" : "127.0.0.1",
      "port" : 50207
    }, {
      "ipAddressV4" : "127.0.0.1",
      "port" : 50208
    } ]
...
```

And now we upload the new address book, this time using the address book admin `0.0.55` as the payer:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -n localhost -p 55 sysfiles upload address-book
```

Finally we re-download the book to see that the hex-encoded cert hash 
and RSA public key were uploaded as expected:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -n localhost -p 2 sysfiles download address-book
Targeting localhost, paying with 0.0.2
Downloading the address-book...OK
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.0 -n localhost -p 2 sysfiles download address-book
Targeting localhost, paying with 0.0.2
Downloading the address-book...OK
$ tail -17 localhost/sysfiles/addressBook.json 
  }, {
    "deprecatedIp" : "127.0.0.1",
    "deprecatedMemo" : "0.0.6",
    "deprecatedPortNo" : 50207,
    "nodeId" : 3,
    "certHash" : "0ae05bde15d216781a40e7bce5303bf68926f9440eec3cb20fabe9df06b0091a205fdea86911facb4e51e46c3890c803",
    "rsaPubKey" : "36363038393964383435373539336535373965653638613332633036303365353939366661626135303864393434383063336562363464636135383738353264613338303537303239336365353365643939326664653438353332346361623564333535653734383133343935346231396332316333663038633631613165356439653335303163343332343539376563346465386464353838666662666464346135663336343662623763353938306362643231646436343031613763393131336631636533313864636134616635373732323462646538396332633137336633666538643039326534623866383030373130376138643965323633333166353335356135383464383037373661306162636139326530303438646433373163666530353936656464366261303737303338313432383866313039613832383035383630363562376262663238353432303434376134343336383830633361393336613666666663646162313031353335633864666561306461306537353035383530346661396163333036396438653166643762623333343530663761346261303439310a",
    "nodeAccount" : "0.0.6",
    "endpoints" : [ {
      "ipAddressV4" : "127.0.0.1",
      "port" : 50207
    }, {
      "ipAddressV4" : "127.0.0.1",
      "port" : 50208
    } ]
  } ]
}
```
 
