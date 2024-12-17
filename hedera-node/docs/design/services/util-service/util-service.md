# Util Service

The Util Service is a service that handles utility operations. 
Currently, the only functionality provided by this service is the generation of 
pseudorandom numbers.

### Table of Contents
- [Util Service](#Util-Service)
- [Protobuf Definitions](#Protobuf-Definitions)
    - [UtilPrngTransactionBody](#UtilPrngTransactionBody)
- [Handlers](#Handlers)
- [Configuration](#Configuration)

## Protobuf Definitions
Protobuf, or Protocol Buffers, is a method of serializing structured data. 
The Util Service uses it to define the structure of our transactions. Here are some of 
the Protobuf definitions used in the Util Service:  

- ```util_service.proto```: This file defines the UtilService which includes the prng RPC 
for generating pseudorandom numbers.  
- ```util_prng.proto```: This file defines the UtilPrngTransactionBody message which is
used in the prng RPC. It includes a range field which, if provided and is positive, 
returns a 32-bit pseudorandom number from the given range in the transaction record. 
If not set or set to zero, will return a 384-bit pseudorandom number in the record.

### UtilPrngTransactionBody
The `UtilPrngTransactionBody` message is a crucial part of the `prng` RPC in the Util Service. 
It is defined in the `util_prng.proto` file and is used to specify the range for the pseudorandom 
number generation.

```
message UtilPrngTransactionBody {
    int32 range = 1;
}
```

The `UtilPrngTransactionBody` message contains a single field, `range`. This field is used to determine the range of the pseudorandom number that will be generated.

- If `range` is provided and is positive, the Util Service will return a 32-bit pseudorandom number from the given range in the transaction record.
- If `range` is not set or set to zero, the Util Service will return a 384-bit pseudorandom number in the transaction record.

This message is used in the `prng` RPC of the `UtilService` defined in `util_service.proto`. 
The `prng` RPC is responsible for generating pseudorandom numbers, and 
the `UtilPrngTransactionBody` message provides the necessary input for this operation.

## Handlers

Handlers are responsible for executing the transactions. Each type of transaction has its 
own handler. All the Handlers implement the TransactionHandler interface and provide 
implementations of pureChecks, preHandle, handle, and calculateFees methods.

- ```UtilHandlers.java```: This class includes a prngHandler which is an instance of 
UtilPrngHandler.  

- ```UtilPrngHandler.java```: This class is a TransactionHandler for handling UTIL_PRNG 
transactions. It uses the n-3 running hash to generate a pseudo-random number. 
The n-3 running hash is updated and maintained by the application, based on the 
record files generated based on preceding transactions. In this way, the number is 
both essentially unpredictable and deterministic.

## Configuration
```UtilPrngConfig``` is a configuration class used in the Hedera Hashgraph network. 
This class is used to configure the behavior of the ```UtilPrngHandler```. The ```UtilPrngConfig``` 
class is a record that contains a single boolean property, isEnabled. This property is 
annotated with ```@ConfigProperty``` and ```@NetworkProperty```, indicating that it can be 
configured via the network's configuration file. The isEnabled property determines whether 
the ```UtilPrngHandler``` allows a transaction to proceed. If isEnabled is set to true, the 
```UtilPrngHandler``` will allow the transaction. If isEnabled is set to false, 
the ```UtilPrngHandler``` will not allow the transaction.