# Yahcli v0.0.1
A command line interface that is able to perform the following actions against a specified network.

1. Account Operations
    1. Get Balance.
2. System File Operations
    1. Download all/specific file.
    2. Upload a System file.
3. Fees Operations
    1. List Basic Transaction and Query fees of all/specific service.

## Getting Started
### Setting up your environment

#### Install Docker



#### Create a folder structure like this.<br>
```
├── config.yml
├── previewnet
│   └── keys
│       ├── account2.pass
│       └── account2.pem
```

The _config.yml_ should have contents like:

```
defaultNetwork: previewnet
networks:
  previewnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 35.231.208.148 }
  stabletestnet:
    nodes:
      - { id: 0, account: 3, ipv4Addr: 34.94.106.61 }
```

You can add details of multiple networks to this config file with appropriate ipv4Addresses.

_{network}/keys/_ folder should contain account{num}.pem and account{num}.pass pair for each account that you want to use for that network.
_num_ here is the account number. 

> Note : Support for multisig accounts is not yet implemented.

### Executing commands

Run the following command:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 help
```

This will list the possible commands that you can execute using the current version deployed to _gcr.io/hedera-registry_

```
Usage: yahcli [-c=yahcli config YAML] [-f=fee to offer] [-n=target network]
                 [-p=payer] [COMMAND]
Perform operations against well-known entities on a Hedera Services network
 -c, --config=yahcli config YAML

 -f, --fixed-fee=fee to offer

 -n, --network=target network

 -p, --payer=payer
Commands:
 help      Displays help information about the specified command
 accounts  Perform account operations
 sysfiles  Perform system file operations
 fees      Perform system fee operations
``` 

For example, to download the feeSchedule from previewnet:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -p 2 -n previewnet sysfiles download fees
```

If you dont specify a destination directory, by default the file will be downloaded to `previewnet/sysfiles/`

## More Examples
### Scenario 1 : Get Account Balance
To get balance of an account.

`accounts` command has a sub command `balance` that retrieves the balance of the accounts that are mentioned as parameters

```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -n previewnet -p 2 accounts balance help

  Usage: yahcli accounts balance <accounts>... [COMMAND]
  Retrieve the balance of account(s) on the target network
        <accounts>...   account names or numbers
  Commands:
    help  Displays help information about the specified command

```

Example run:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -n localhost -p 56 accounts balance 56 50
  
  Targeting localhost, paying with 0.0.56
   ---------------------|----------------------|
             Account Id |              Balance |
   ---------------------|----------------------|
                 0.0.50 |            425643195 |
                 0.0.56 |         898862928799 |

```

### Scenario 2 : Get Fee for Services
To get fee for the most basic transaction/query service.

`fees` command has a subcommand `list-base-prices` that takes in a service as a parameter and lists fees of all basic transactions and queries possible in that service.

```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -n localhost -p 56 fees list-base-prices help

  Usage: yahcli fees list-base-prices <services>... [COMMAND]
  List base prices for all operations
        <services>...   services ('crypto', 'consensus', 'token', 'file',
                          'contract', 'scheduled')
                        or 'all' to get fees for all basic operations
  Commands:
    help  Displays help information about the specified command
```

Example Run:
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -n localhost -p 56 fees list-base-prices crypto

-------------------------------|-----------------|
    Transaction and Query Fees |  		         |
-------------------------------|-----------------|
-------------------------------|-----------------|
        Cryptocurrency Service | Fees 		     |
-------------------------------|-----------------|
                  cryptoCreate | 0.04992 	     |
                  cryptoUpdate | 0.00021 	     |
                cryptoTransfer | 0.00010 	     |
       cryptoGetAccountRecords | 0.00010 	     |
          cryptoGetAccountInfo | 0.00010 	     |
                  cryptoDelete | 0.01122 	     |
```

### Scenario 3 : Updating Throttles
To update throttles put on services.

Run the following command to download the `application.properties` file.
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -p 2 -n previewnet sysfiles download props
```

Human Readable `application.properties` file will be downloaded to `previewnet/sysfiles/` folder.
Update the required throttles in the properties file and perform a sysfile upload operation.

```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -p 2 -n previewnet sysfiles upload props
```

### Scenario 4 : Enable/Disable a Service
To Enable/Disable permission for an operation.

Run the following command to download the `api-permission.properties` file.
```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -p 2 -n previewnet sysfiles download permissions
```

Human Readable `api-permission.properties` file will be downloaded to `previewnet/sysfiles/` folder.
Update the file by Enabling or Disabling a service and perform a sysfile upload operation. 

```
$ docker run -v $(pwd):/launch gcr.io/hedera-registry/yahcli:0.0.1 -p 2 -n previewnet sysfiles upload permissions
```