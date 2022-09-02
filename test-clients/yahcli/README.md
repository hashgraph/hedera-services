# Yahcli v0.2.6
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
4. [Sending funds to an account](#sending-account-funds)
4. [Creating a new account](#creating-a-new-account)
6. [Updating system files](#updating-system-files)
7. [Validating network services](#validating-network-services)
8. [Preparing for an NMT upgrade](#preparing-an-nmt-software-upgrade)
9. [Launching an NMT telemetry upgrade](#launching-an-nmt-telemetry-upgrade)
10. [Scheduling a network freeze](#scheduling-a-network-freeze)
11. [Re-keying an account](#updating-account-keys)
12. [Getting deployed version info of a network](#get-version-info)
13. [Creating a new key](#generate-a-new-ed25519-key)
14. [Printing key details](#printing-key-details)

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
we can add information about the stable testnet. And we can override the 
default payer account or default node account for any network using the 
`defaultPayer` and `defaultNodeAccount` fields, respectively. 

We can also use the command line option `-p` to override `defaultPayer`, and 
the command line option `-a/--node-account` to override `defaultNodeAccount`. 

It is also possible to use the `-i/--node-ip` option to choose a target 
node by its IP adress. However, if the IP address given to the `-i` option does 
not appear in the _config.yml_, then we **must** explicitly give its node
account via the `-a` option. So with the above _config.yml_, it is enough to do,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 2 -n previewnet \
> -i 35.231.208.148
```

...since the ip `35.231.208.148` is in the _config.yml_. But to use an IP not
in the _config.yml_, we must also specify the node account, 
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 2 -n previewnet \
> -i 35.199.15.177 -a 4
```

```
defaultNetwork: previewnet

networks:
  previewnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 35.231.208.148 }
  stabletestnet:
    defaultPayer: 50
    defaultNodeAccount: 4
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
$ docker run -it -v $(pwd):/launch yahcli:0.2.6 -p 2 sysfiles download all 
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 help
``` 

:information_desk_person: &nbsp; Since the only key we have for previewnet
is for account `0.0.2`, we will need to use `-p 2` for the payer argument 
when running against this network.

To download the fee schedules from previewnet given the config above, we run,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 2 -n previewnet sysfiles download fees
Targeting previewnet, paying with 0.0.2
Downloading the fees...OK
$ ls previewnet/sysfiles/
feeSchedules.json
```

The fee schedules were downloaded in JSON form to _previewnet/sysfiles/feeSchedules.json_.
To see more options for the `download` subcommand (including a custom download directory), 
we run,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 sysfiles download help
```

The remaining sections of this document focus on specific use cases.

# Getting account balances
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n previewnet -p 2 accounts balance 56 50
```

# Sending account funds
You can send funds from the default payer's account to a beneficiary account, in denominations of `tinybar`, `hbar`, or `kilobar`.

The default denomination is `hbar`. To change the memo for the `CryptoTransfer`, use the `--memo` option.

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n previewnet -p 2 accounts send --denomination hbar --to 58 --memo "Yes or no" 1_000_000

```

# Creating a new account
You can also create an entirely new account with an optional initial balance (default `0`) and memo (default blank).

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n previewnet -p 2 accounts create -d hbar -a 1 --memo "Who danced between"

```

The new account will have a single Ed25519 key, exported to the target network's _keys/_ directory in both 
PEM/passphrase and mnemonic forms. (E.g., this command might create both _previewnet/keys/account1234.{pem,pass}_ and 
_previewnet/keys/account1234.words_.)

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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 2 sysfiles download address-book
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 55 sysfiles upload address-book
```

Finally we re-download the book to see that the hex-encoded cert hash and RSA public key were uploaded as expected:
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 2 sysfiles download address-book
Targeting localhost, paying with 0.0.2
Downloading the address-book...OK
$ tail -17 localhost/sysfiles/addressBook.json 
  }, {
    "nodeId" : 3,
    "certHash" : "0ae05bde15d216781a40e7bce5303bf68926f9440eec3cb20fabe9df06b0091a205fdea86911facb4e51e46c3890c803",
    "rsaPubKey" : "3636303839396438343537353933653537396565363861333263303630336535393936666162613530386439343438306333656236346463613538373835326461333830353730323933636535336564393932666465343835333234636162356433353565373438313334393534623139633231633366303863363161316535643965333530316334333234353937656334646538646435383866666266646434613566333634366262376335393830636264323164643634301216763393131336631636533313864636134616635373732323462646538396332633137336633666538643039326534623866383030373130376138643965323633333166353335356135383464383037373661306162636139326530303438646433373163666530353936656464366261303737303338313432383866313039613832383035383630363562376262663238353432303434376134343336383830633361393336613666666663646162313012153335633864666561306461306537353035383530346661396163333036396438653166643762623333343530663761346261303439310a",
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
 
## Uploading special files

System files in the range `0.0.150-159` are _special files_ that do not have the normal 1MB size limit. 
These are used to stage ZIP artifacts for an NMT software or telemetry upgrade. By default, file `0.0.150`
is used for a software update ZIP, and file `0.0.159` for a telemetry upgrade ZIP. 

:warning:&nbsp;Only three accounts have permission to update the special files: `0.0.2`, `0.0.50`, and `0.0.58`.

To upload such artifacts, use the special files names as below,
```
$ tree localhost/sysfiles/
localhost/sysfiles/
├── softwareUpgrade.zip
└── telemetryUpgrade.zip
```

Then proceed as with any other `sysfiles upload` command, 
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 sysfiles upload software-zip
...
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 sysfiles upload telemetry-zip
...
```

:repeat:&nbsp;Since `yahcli:0.2.6` you can add the `--restart-from-failure` option like,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 sysfiles upload software-zip --restart-from-failure
```

If the hash of the file on the network matches the hash of a prefix of the bytes you're uploading, then yahcli will
automatically restart the upload after that prefix. For example,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 sysfiles upload software-zip
Log level is WARN
Targeting localhost, paying with 0.0.2
.i. Continuing upload for 0.0.150 with 34 appends already finished (out of 97 appends required)
...
```

### Checking a special file hash

You can also directly check the SHA-384 hash of a special file with the `sysfiles hash-check` subcommand,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 sysfiles hash-check software-zip
```

### Recovering from a failed special file upload


# Validating network services

:building_construction:&nbsp;**TODO** the _ValidationScenarios.jar_ functionality to be migrated here.

Services will be validated by type; to see all supported options, run,

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 2 validate help
```

# Preparing an NMT software upgrade

To prepare for an automatic software upgrade, there must exist a system file in the range `0.0.150-159` 
(by default, `0.0.150`) that is a ZIP archive with artifacts listed in the [NMT requirements document](https://github.com/swirlds/swirlds-docker/blob/main/docs/docker-infrastructure-design.md#toc-phase-1-feat-hedera-node-protobuf-defs-current). The expected 
SHA-384 hash of this ZIP must be given so the nodes can validate the integrity of the upgrade file before 
staging its artifacts for NMT to use. This looks like,

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 prepare-upgrade \
> --upgrade-zip-hash 5d3b0e619d8513dfbf606ef00a2e83ba97d736f5f5ba61561d895ea83a6d4c34fce05d6cd74c83ec171f710e37e12aab
```

# Launching an NMT telemetry upgrade

To perform an automatic telemetry upgrade, there must exist a system file in the range `0.0.150-159` 
(by default, `0.0.159`) that is a ZIP archive with artifacts listed in the [NMT requirements document](https://github.com/swirlds/swirlds-docker/blob/main/docs/docker-infrastructure-design.md#toc-phase-1-feat-hedera-node-protobuf-defs-current). The expected 
SHA-384 hash of this ZIP must be known so the nodes can validate the integrity of the upgrade file before 
staging its artifacts for NMT to use.  This looks like,

```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 upgrade-telemetry \
> --upgrade-zip-hash 8ec75ab44b6c8ccac4a6e7f7d77b5a66280cad8d8a86ed961975a3bea597613f83af9075f65786bf9101d50047ca768f \
> --start-time 2022-01-01.00:00:00
```

# Scheduling a network freeze

Freeze start times are (consensus) UTC times formatted as `yyyy-MM-dd.HH:mm:ss`. Freezes may be
both scheduled and aborted; and a scheduled freeze may also be flagged as the trigger for an NMT
software upgrade.

A vanilla freeze with no NMT upgrade only includes the start time, 
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 freeze \
> --start-time 2022-01-01.00:00:00
```

While a freeze that should trigger a staged NMT upgrade uses the `freeze-upgrade` variant,
which **must** repeat the hash of the intended update, 
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 freeze-upgrade \
> --upgrade-zip-hash 5d3b0e619d8513dfbf606ef00a2e83ba97d736f5f5ba61561d895ea83a6d4c34fce05d6cd74c83ec171f710e37e12aab
> --start-time 2021-09-09.20:11:13 
```

To abort a scheduled freeze, simply use the `freeze-abort` command,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n localhost -p 58 freeze-abort 
```

# Updating account keys

You can use yahcli to replace an account's key with either a newly generated key, or an existing key. (Existing keys
can be either PEM files or BIP-39 mnemonics.) 

Our first example uses a randomly generated new key,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 2 -n localhost \
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 57 -n localhost \
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
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -p 57 -n localhost \
> accounts rekey -k new-account57.words 57
Targeting localhost, paying with 0.0.2
.i. Exported key from new-account55 to localhost/keys/account57.pem
.i. SUCCESS - account 0.0.57 has been re-keyed
```

# Get version info
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 -n previewnet -p 2 version
```

# Generate a new Ed25519 key

You can use yahcli to generate a new Ed25519 key in PEM and mnemonic forms; note that 
ECDSA(secp256k1) keys are not yet supported. The most common pattern will likely be,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 keys gen-new -p novel.pem
.i. Generating a new key @ novel.pem
.i.  - The public key is: 4351607d4a00821e6cbd8e8c186bfa3a2b8fdb5ca81cf1e5f84e95a86875fd84
.i.  - Passphrase @ novel.pass
.i.  - Mnemonic form @ novel.words
.i.  - Hexed public key @ novel.pubkey
.i.  - DER-encoded private key @ novel.privkey
$ cat novel.pass
PkpcBBYCjd7K
$ cat novel.words
wait busy pull hobby antique connect case mammal museum hockey fox refuse settle twist snow topple culture clean alpha hair people donkey surface thought
```

Note this command does *not* require setting a target network or payer account. If desired, 
you can you can choose the PEM passphrase with the `-x` option instead of getting a randomly
generated passphrase in a _.pass_ file.

# Printing key details

If you have a PEM or mnemonic file for an Ed25519 key pair and need to extract the public key, you can run,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 keys print-public -p novel.pem -x PkpcBBYCjd7K
.i. The public key @ novel.pem is: 4351607d4a00821e6cbd8e8c186bfa3a2b8fdb5ca81cf1e5f84e95a86875fd84
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 keys print-public -p novel.words
.i. The public key @ novel.words is: 4351607d4a00821e6cbd8e8c186bfa3a2b8fdb5ca81cf1e5f84e95a86875fd84 
```

If you need both the public and private keys, use instead the `print-keys` subcommand,
```
$ docker run -it -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.2.6 keys print-keys -p novel.pem -x PkpcBBYCjd7K
.i. The public key @ novel.pem is : 4351607d4a00821e6cbd8e8c186bfa3a2b8fdb5ca81cf1e5f84e95a86875fd84
.i. The private key @ novel.pem is: ea52bce1ad54a88e156f50840e856b941f9b0db09266660c953cd14205546ca2
.i.   -> With DER prefix; 302e020100300506032b657004220420ea52bce1ad54a88e156f50840e856b941f9b0db09266660c953cd14205546ca2
```
