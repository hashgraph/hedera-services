# Yahcli v0.1.4
Yahcli (_Yet Another Hedera Command Line Interface_) supports DevOps
actions against the Hedera networks listed in a _config.yml_ file.

Actions include updating system files, running validation tests,
re-keying accounts, and freezing networks for maintenance.

:warning:&nbsp;Besides the _config.yml_, yahcli requires keys and
other assets to be present in a specific directory layout. The details
appear below. 

**Table of contents**
1. [Setting up the working directory](#setting-up-the-working-directory)
2. [Understanding general usage](#general-usage)
3. [Checking account balances](#getting-account-balances)
4. [Updating system files](#updating-system-files)
5. [Validating network services](#validating-network-services)
6. [Scheduling a network freeze](#scheduling-a-network-freeze)
7. [Re-keying an account](#updating-account-keys)

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

We can add details for multiple networks to this config file; for example,
we could add information about stabletestnet.
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

For each network we add, we need a _{network}/keys/_ folder 
that contains a `account{num}.pem` for each account we will 
use with that network. :guard: &nbsp; If there is no corresponding
`account{num}.pass` for a PEM file, please be ready to enter 
the passphrase interactively in the console. For example,
```
$ docker run -it -v $(pwd):/launch yahcli:0.1.4 -p 2 sysfiles download all 
Targeting localhost, paying with 0.0.2
Please enter the passphrase for key file localhost/keys/account2.pem: 
```

:turtle: &nbsp; The docker image needs to launch a JAR, which is fairly slow. 
Please allow a few seconds for the the above command to run.

:warning:&nbsp;Without the `-it` flags above, Docker will not attach
STDIN as a TTY, and you will either not be prompted for the passphrase,
or your passphrase will appear in clear text.

Note that yahcli does not support multi-sig accounts.

# General usage

To list all available commands,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 help
``` 

:information_desk_person: &nbsp; Since the only key we have for previewnet
is for account `0.0.2`, we will need to use `-p 2` for the payer argument 
when running against this network.

To download the fee schedules from previewnet given the config above, we run,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -p 2 -n previewnet sysfiles download fees
Targeting previewnet, paying with 0.0.2
Downloading the fees...OK
$ ls previewnet/sysfiles/
feeSchedules.json
```

The fee schedules were downloaded in JSON form to _previewnet/sysfiles/feeSchedules.json_.
To see more options for the `download` subcommand (including a custom download directory), 
we run,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 sysfiles download help
```

The remaining sections of this document focus on specific use cases.

# Getting account balances
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n previewnet -p 2 accounts balance 56 50
```

# Updating system files
For this example, we will run against a `localhost` network since we will modify a system file.

Our goal in the example is to add a completely new address book entry for a node with `nodeId=3`. 
The DER-encoded RSA public key of the node is in a file _node3.der_, and its TLS X509 cert is 
in a file _node3.crt_. We place these files in the directory structure below.
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 2 sysfiles download address-book
Targeting localhost, paying with 0.0.2
Downloading the address-book...OK
```

Next we edit the newly-downloaded _localhost/sysfiles/addressBook.json_ and 
add a new entry with `nodeId=3`, as below.

:information_desk_person: &nbsp; By using the `'!'` character in the `certHash` and `rsaPubKey` fields,
we tell yahcli to compute their values from the _certs/node3.crt_ and _pubkeys/node3.der_
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 55 sysfiles upload address-book
```

Finally we re-download the book to see that the hex-encoded cert hash and RSA public key were uploaded as expected:
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 2 sysfiles download address-book
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
 
In some cases, yahcli does client-side validation to catch errors early. 
For example, **all three** of the `deprecated*` fields must be set to "reasonable"
values. Suppose we try to update the address book again, changing the 
`deprecatedMemo` field to something other than an account literal,
```
...
  }, {
    "deprecatedIp" : "127.0.0.1",
    "deprecatedMemo" : "This node is the best!",
    "deprecatedPortNo" : 50207,
    "nodeId" : 3,
...
```

We then get a messy error and the update aborts before sending
any `FileUpdate` transaction to the network:
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 55 sysfiles upload address-book
Targeting localhost, paying with 0.0.55
java.lang.IllegalStateException: Deprecated memo field cannot be set to 'This node is the best!'
	at com.hedera.services.bdd.suites.utils.sysfiles.serdes.AddrBkJsonToGrpcBytes.toValidatedRawFile(AddrBkJsonToGrpcBytes.java:70)
	at com.hedera.services.bdd.suites.utils.sysfiles.serdes.AddrBkJsonToGrpcBytes.toValidatedRawFile(AddrBkJsonToGrpcBytes.java:35)
	at com.hedera.services.yahcli.suites.SysFileUploadSuite.appropriateContents(SysFileUploadSuite.java:97)
	at com.hedera.services.yahcli.suites.SysFileUploadSuite.uploadSysFiles(SysFileUploadSuite.java:70)
	at com.hedera.services.yahcli.suites.SysFileUploadSuite.getSpecsInSuite(SysFileUploadSuite.java:65)
...
```

# Validating network services

Services are validated by type; as usual, to see all options, run,

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 2 validate help
```

# Scheduling a network freeze

A freeze time (in consensus UTC) is specified in the pattern `yyyy-MM-dd.HH:mm:ss`; for example,

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -n localhost -p 2 freeze --start-time 2021-09-09.20:11:13
```

# Updating account keys

You can use yahcli to replace an account's key with either a newly generated key, or an existing key. (Existing keys
can be either PEM files or BIP-39 mnemonics.) 

Our first example uses a randomly generated new key,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -p 2 -n localhost \
> accounts rekey --gen-new-key 57
Targeting localhost, paying with 0.0.2
.i. Exported a newly generated key in PEM format to localhost/keys/account57.pem
.i. SUCCESS - account 0.0.57 has been re-keyed
```

This leaves the existing key info under _localhost/keys_ with _.bkup_ extensions, and overwrites
_localhost/keys/account57.pem_ and _localhost/keys/account57.pass_ in-place.
```
$ tree localhost/keys
localhost/keys
├── account2.pass
├── account2.pem
├── account57.pass
├── account57.pass.bkup
├── account57.pem
└── account57.pem.bkup
```

For the next example, we specify an existing PEM file, and enter its passphrase when prompted,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -p 57 -n localhost \
> accounts rekey -k new-account57.pem 57
Targeting localhost, paying with 0.0.2
Please enter the passphrase for key file new-account55.pem: 
.i. Exported key from new-account55 to localhost/keys/account57.pem
.i. SUCCESS - account 0.0.57 has been re-keyed
```

In our final example, we replace the `0.0.57` key from a mnemonic,
```
$ cat new-account57.words
goddess maze eternal small normal october ... author
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.1.4 -p 57 -n localhost \
> accounts rekey -k new-account57.words 57
Targeting localhost, paying with 0.0.2
.i. Exported key from new-account55 to localhost/keys/account57.pem
.i. SUCCESS - account 0.0.57 has been re-keyed
```
