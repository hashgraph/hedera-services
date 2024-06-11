# Design proposal TSS Cryptography API

## Summary

Provide necessary pieces for signing blocks with TSS

| Metadata           | Entities                                               | 
|--------------------|--------------------------------------------------------|
| Designers          | Austin, Cody, Edward, Rohit, Maxi, <br/> Platform team | 
| Functional Impacts | Platform team. Release engineering. Devops             |
| Related Proposals  | TSS                                                    |
| HIPS               | ?                                                      |


## Purpose and Context
The goal of a threshold signature scheme is to enable a group of participants (shareholders) to securely and efficiently perform signature generation while preserving security even in the presence of up to a threshold number of adversary parties.

This proposal covers the implementation of all necessary pieces to provide the consensus-node and block-node, the functionality to sign and verify blocks using Threshold Signature Scheme (TSS), and EC Cryptography.

The overview of the process and background for TSS and how impacts functionally of the platform can be found [here](https://www.notion.so/swirldslabs/TSS-Threshold-Signature-Scheme-9e2676cc4f2a4650a8c0c33b774f3cf4).

### Glossary
- **TSS (Threshold Signature Scheme)**: A threshold-based signing where a minimum number of parties (threshold) must collaborate to produce or verify a signature.
- **Shamir’s Secret Sharing**: In Shamir’s SS, a secret s is divided into n shares by a dealer, and shares are sent to shareholders secretly. The secret "s" is shared among n shareholders in such a way that (a) with t or more than t shares can recover the secret, and (b) with fewer than t shares cannot obtain the secret. Shamir’s (t, n) SS is based on a linear polynomial and is unconditionally secure.
- **Groth 21**: A zero-knowledge proof system developed by Jens Groth in 2021. It is a non-interactive zero-knowledge (zk-SNARK) protocol, which allows one party (the prover) to convince another party (the verifier) that a statement is true without revealing any information beyond the validity of the statement itself.
- **ElGamal**: In 1985 ElGamal proposed a randomized signature scheme called ElGamal signature scheme. On a message and a random number chosen by a signer, ElGamal signature scheme produces a signature consisting of two numbers (r, s), where r is computed from a random number regardless of a message and then s is computed from a message, a signer's secret key, a chosen random number, and r.
- **EC ([Elliptic-Curves](https://crypto.stanford.edu/pbc/notes/elliptic))**: "Elliptic" is not elliptic in the sense of an "oval circle". In the field "Fp", an EC is like a non-connected cloud of points. All points satisfy an equation and all operations are performed modulo p. Some elliptic curves are pairing-friendly.
- **Bilinear Pairings**: Are mathematical functions used in cryptography to map two elements of different groups (in EC, the group is an elliptic curve) to a single value in another group in a way that preserves certain algebraic properties.
- **Fields**: Mathematical structures where addition, subtraction, multiplication, and division are defined and behave as expected (excluding division by zero).
- **Groups**: Sets equipped with an operation (like addition or multiplication) that satisfies certain conditions (closure, associativity, identity element, and inverses).
- **Share**: Represents a piece of the public/private necessary elements to create or verify a signature. In TSS, a threshold number of shares is needed to validate a signature. To represent weight, any participant can have more than 1 share.

### Goals
- **Usability:** Design a user-friendly library that is easy to integrate with consensus-node code and block-node code.
- **EVM support:** Our implementation must be supported by EVM.
- **Security:** Our APIS should use audited code and be able to pass security audits both internal and external.
- **Flexibility**: Minimize the impact of introducing support for other elliptic curves. 
- **Independent Release:** Design release cycle independent components that can be used both in the consensus node and in the future block node.

### Non-Goals
- Build our own cryptography library implementation that supports EC-curves from the gecko in Java.

### External References
- https://andrea.corbellini.name/2015/05/17/elliptic-curve-cryptography-a-gentle-introduction/
- https://www.iacr.org/archive/asiacrypt2001/22480516.pdf
- https://hackmd.io/@benjaminion/bls12-381#Motivation
- https://www.johannes-bauer.com/compsci/ecc/

## Changes

### Core Behaviors

The proposed TSS solution combines EC-Cryptography primitives that allow for short and aggregate signatures, ElGamal secret sharing scheme for the distribution of the secret elements to each of the participants, and multiple assignations of shares per shareholder to represent the participant's weight in the signature.

#### Description of the Scheme
##### Input
* Participant EC private key (Private to each participant)
* Number of participants (Public)
* Amount of shares per participant (Public)
* Each participant distinct public key (Public)
##### 1. Ensemble participant share claim directory
A map that identifies each share uniquely, distributes the shares over the shareholders given their respective assigned amount.

e.g: In a scheme distributing 10 shares over 4 participants
```
S₁	S₂	S₃	S₄	S₅	S₆	S₇	S₈	S₉	S₁₀
P₁	P₁	P₁	P₁	P₁	P₂	P₂	P₃	P₄	P₄
```

(Shamir’s Secret Sharing)
##### 2. Create shares
`Share`: A data structure consisting of:
* a unique identifier,
 * an EC private key,
* and an EC public Key.

Each shareholder can have 1 or more shares.

##### 3. Create TSSMessages 
`TSSMessage`: A data structure for distributing private information among the participants.
Each shareholder will split each share's contained public key into t parts (t = the number of participants) and encrypt each part (participant's portion of the key) using the participant's public EC key on 0.
Finally, sign the message using the creator's own EC private key of 0.

##### 4. Distribution of TSSMessages
Using a channel established outside the scope of the scheme, each participant will distribute the tssMessage to the other participants in a limited time window.

##### 5. Collection of TSSMessages
Each participant will be waiting for other participants' messages to arrive for a limited amount of time.

##### 6. Validation of TSSMessages
Each participant will validate the received message signature using the sender's public key.

##### 7. Decryption of TSSMessages
Using the EC Private Key, each participant will decrypt other participant shares' pieces encrypted with its own public key.

##### 8. Creating an aggregated Public Key
After decrypting, each participant will use EC cryptography primitives to aggregate each received public key

### Architecture

![img_6.png](img_6.png)

1. **TSS Library**: Consensus node will use the TSS library to create shares, create TSS messages to send to other nodes, ensemble shared public keys (ledgerId), and sign the block-node merkle tree hash.
2. **Bilinear Pairings Signature Library**: Provides cryptographic objects (PrivateKey, PublicKey, and Signature) and operations for the block-node and consensus-node to sign and verify the signatures in the block. Consensus-node uses this library indirectly through the TSS Library.
3. **Bilinear Pairings API**: Generalization to be included at compilation time providing the cryptography primitives and the arithmetic operations for working with a specific EC curve and the underlying Groups, Fields, and Pairings.
4. **Bilinear Pairings Implementation**: Java underlying implementation of the previous API, that will be loaded at runtime using Java's SPI. Multiple implementations can be provided for supporting different types of curves.
5. **Native Support Library**: Provides a set of generic functions loading native libraries in different system architectures when packaged in a jar using a predefined organization, so they can be accessed with JNI.
6. **Arkworks Rust Library**: arkworks is a Rust ecosystem for zkSNARK programming. Libraries in the arkworks ecosystem provide efficient implementations of all components required to implement zkSNARK applications, from generic finite fields to R1CS constraints for common functionalities.
7. **EC-Key Utils**: Utility module to enable the node operator to generate pre-genesis initial public/private Key pair

### Module Organization and repositories
![img_7.png](img_7.png)
1. **hedera-cryptography**: This is a separate repository for hosting cryptography-related libraries. This repository is necessary as a means of facilitating our build process that includes Rust libraries. Also provides independent release cycles between consensus node code and block node code.
2. **swirlds-native-support**: Gradle module that enables loading into memory compiled native libraries, so they can be used with JNI.
3. **swirlds-cryptography-tss**: Gradle module for the TSS Library. This library-only client is the consensus node, so will remain close to it in `hedera-services` repository under `platform-sdk` folder.
4. **swirlds-cryptography-signature-library**: Gradle module for the Bilinear Pairings Signature Library.
5. **swirlds-cryptography-pairings-api**: Gradle module for the Bilinear Pairings API. Minimizes the impact of adding or removing implementations.
6. **swirlds-cryptography-alt128**: Gradle module that will provide the implementation for the Bilinear Pairings API using alt-128 elliptic curve. That curve has been chosen in accordance with EVM's support of it. Support for that curve will be provided by arkworks rust library. The module will include Java code, and rust code that will be compiled for all possible system architectures, and distributed in a jar under a predefined structure.


### Libraries Specifications

#### Swirlds Native support
##### Overview
Our implementation of the `swirlds-cryptography-pairings-api` will use native compiled libraries under the hood accessed with JNI.

There are two possible ways of making the library accessible to Java code, so it can be accessed through JNI:
1. Make it available in the classpath: The library would need to be previously installed in the executing environment and reference its location in the classpath env variable.
2. Distribute it as an application dependency. The caveat here is that:

    a. The distributed library needs to be compiled in every possible architecture the application is going to be executed on.

    b. The library needs to be unpackaged from the jar as it cannot be accessed while it's compressed.


The native-support library will help with the loading of the native library by implementing option 2.
for this to work, the Java native libraries need to be included in the jar running the JNI wrapping code under the following structure:

```
 native-library-client.jar
 |_WEB-INF
    |_arch64
      |_macos
        |_libhedera_bls_jni.dylib
    |amd64
      |_macos
        |_libhedera_bls_jni.dylib
      |_linux
        |_libhedera_bls_jni.so
      |_windows
        |_libhedera_bls_jni.dll
    |x-86
      |_linux
        |_libhedera_bls_jni.so
      |_windows
        |_libhedera_bls_jni.dll
  ...
```

lib folder is organized in subfolders by the platform identifier, as returned by `System.getProperty("os.arch")` and `System.getProperty("os.name")` 

##### Constraints
This module will not depend on hedera-services artifacts, so cannot include logging, metrics, configuration, or any other helper module from that repo.

##### Public API
###### `LibraryDescriptionEntry`
**Description**: Given that the compilation of a native library produces files with different names under different OS and architectures, we need a way to ensemble a catalog for all possible forms our library will take.

A record of 3 elements that defines the name of the binary file of the library to load in a specific system architecture and OS.

**Example**: 
```java
    new LibraryDescriptionEntry("macos", "aarch64", "libhedera_bls_jni.dylib");
```
###### `LibraryDescription`
**Description**: A description of the library in all possible systems.
```java
    static final LibraryDescription LIB_HEDERA_BLS = new LibraryDescription(new LibraryDescriptionEntry("macos", "aarch64", "libhedera_bls_jni.dylib"), new LibraryDescriptionEntry("linux", "amd64", "libhedera_bls_jni.so"), ...);
```
If the library name is the same for all system architectures with the only change of the extension one can configure:
```java
  //For any system architecture load libhedera_bls_jni.dylib, libhedera_bls_jni.so or libhedera_bls_jni.dll depending on the os  
  static final LibraryDescription LIB_HEDERA_BLS = new LibraryDescription("libhedera_bls_jni");
```


###### `LibraryLoader`
**Description**: Helper class that will load a library for the correct system:

```java
class AnySystemReferencedClass{
    static {
        LibraryLoader.load(LIB_HEDERA_BLS);
    }
}
```
if there is no description matching the current system architecture the method will fail with runtime exception.


#### Swirlds Cryptography Pairings Signature Library
##### Overview
This module provides cryptography primitives to create EC PublicKeys, EC PrivateKeys, and Signatures.


##### Public API
###### `SignatureSchema`
**Description**: A pairings signature scheme can be implemented with different types of curves and with different group assignment configurations.
E.g.: BLS_12_381 curve using Group1 of the Pairing for generating public key elements. A different result will be produced  if we configure
the same curve but group 2 for public key elements.
###### `PairingPrivateKey`
**Description**: A private key generated using the pairings API
###### `PairingPublicKey`
**Description**: A public key generated using the pairings API
###### `PairingSignature`
**Description**: A signature generated with the private key that can be verified with the public key

##### Implementation Note
The serialization of the elements in this module adds a byte to represent the combination of Curve type and group assignment.


##### Examples
###### Generating a SignatureSchema
```java
SignatureSchema signatureSchema = SignatureSchema.from(Curve.ALT_BN128, GroupAssignament.G1_PUBLIC_KEYS);
```
###### Generating a Private Key
```java
   import java.security.SecureRandom;

   PairingPrivateKey pk = PairingPrivateKey.create(signatureSchema, new SecureRandom());
   
```
###### Generating a Public Key
```java
   PairingPublicKey pbk = PairingPublicKey.create(pk);
   
```
###### Generating a Signature
```java
    byte[] message = new byte[]{};
    PairingSignature s = PairingSignature.sign(pk, message );
   
```
###### Verifying a Signature
```java
    s.verify(pbk, message);
```
##### Constraints
This module will not depend on hedera-services artifacts, so cannot include logging, metrics, configuration, or any other helper module from that repo.
##### Dependencies
swirlds-cryptography-pairings-API and runtime implementation
##### Other considerations
Analyzed the possibility of implementing [JCA](https://docs.oracle.com/en/java/javase/11/security/java-cryptography-architecture-jca-reference-guide.html#GUID-9A793484-AE6A-4513-A603-BFEAE887DD8B) (Java-Cryptography-architecture).
There were unknowns that are worth investigating in a follow-up task:
 * Should we parametrize the EC curve with: [`java.security.spec.EllipticCurve`](https://docs.oracle.com/javase/1.5.0/docs/api/java/security/spec/EllipticCurve.html)? Implications?
 * What is the serialization format supported by arkworks?  Raw Key Bytes are formatted with PKCS#8 for private keys and X.509 for public keys. Should we define a custom format for bytes serialized with arkworks? Should we reformat? What do we do with our custom content.


#### Swirlds Threshold Signature Scheme (TSS) Library 
STILL WIP
##### Overview
##### Constraints
##### Public API
###### `Tss`
**Link**: [Tss](tss-library%2FTss.java)

**Description**: Provides all necessary operations for 


##### Example

###### Get a concrete Tss Implementation out of a [SignatureSchema](#Swirlds-Cryptography-Pairings-Signature-Library)
```java
  Tss tssImpl = TSS.getFor(signatureSchema);
```

######  Creating TssShareClaims
```java
//Assuming NODE_0
int currentNodeIndex = NODE_0;

//Load the keys from disk:
PairingPrivateKey currentNodePrivateKey = Platform.loadECKeyFromDisk();
PairingPublicKey node1PbK = Platform.loadECPublicKeyFromDisk("NODE1");
PairingPublicKey node2PbK = Platform.loadECPublicKeyFromDisk("NODE2");

//Prepare meta info from address-book
TssShareClaims tssShareClaims =  tssImpl.getShareClaims(
        List.of(
                new ShareClaimInfo(NODE_0, PairingPublicKey.create(currentNodePrivateKey),50),
                new ShareClaimInfo(NODE_1, node1PbK,20),
                new ShareClaimInfo(NODE_2, node2PbK,30)
        ));
```
###### Generate Node's TssPrivateShare
```java
//Assuming NODE_0
TssShareId shareId = tssImpl.createShareId(currentNodeIndex) ;
TssPrivateShare privateShare = new TssPrivateShare(shareId, currentNodePrivateKey);
```

###### Generate TSSMessage
```java
int threshold = Platform.threshold(); //This is an external parameter
TssMessage message = tssImpl.generateTssMessage(RANDOM, tssShareClaims, privateShare,  threshold);
```

######  Processing TSSMessages
```java
//Once a list TSSMessages has been collected
List<TssMessage> messageList = Platform.collectTssMessages();
//Filter valid messages
List<TssMessages> validMessages = messageList.strem().filter(m-> m.verify(currentNodePublicKey, tssShareClaims)).toList();
//Getting private shares. Will return null if fail
List<TssPrivateShare> privateShares = tssImpl.decryptPrivateShares(currentNodeIndex, currentNodePrivateKey, tssShareClaims, threshold, validMessages);
//Process public shares. Will return null if fail
List<TssPublicShare> publicShares = tssShareClaims.claims()
        .stream()
        .map(claim -> tssImpl.computePublicShare(claim.shareId(), threshold, validMessages))
        .filter(Objects::nonNull)
        .toList();
```
######  Signing messages
```java
//for each private share of the node:
for (TssPrivateShare share : privateShares){ 
    TssSignature signature = share.sign(blockHash);
    Platform.send(signature);
}

//collect signatures
List<TssShareSignature> collectedSignatures = Platform.collectTssSignatures();
List<TssShareSignature> validSignatures = new ArrayList<>();
for (TssShareSignature signature : collectedSignatures){
    if(signature.signature().verifySignature(tssShareIdTssPublicShareMap.get(signature.shareId()).publicKey(),
blockHash)){
        validSignatures.add(signature);
            }
}
```
######  Aggregate Signatures
```java
//collect signatures
List<TssShareSignature> collectedSignatures = Platform.collectTssSignatures();
List<TssShareSignature> validSignatures = new ArrayList<>();
for (TssShareSignature signature : collectedSignatures){
    if(signature.signature().verifySignature(tssShareIdTssPublicShareMap.get(signature.shareId()).publicKey(),
blockHash)){
        validSignatures.add(signature);
            }
}

PairingSignature value = TssUtils.aggregateSignatures(validSignatures);
```

#### Swirlds Cryptography Pairings API
##### Overview
This API will expose general arithmetic operations to work with Billinear Pairings and EC curves that implementations must provide.

##### Public API

###### `Curve`
**Description**: Represents the different types of elliptic curves. Implementations of this api should decide if they provide any, one or many curves.

**Link**: [Curve.java](pairings-api%2FCurve.java)

**Note**: This enum should list all possible types so changes in the implementation don't change the API, but as of now, the amount is limited.

###### `BilinearPairing`

**Description**: This class provides access to each of the groups (G₁, G₂) for a specific Pairing and the FiniteField associated with the curve.

**Link**:  [BilinearPairing.java](pairings-api%2FBilinearPairing.java)

###### `Field`
**Description**: This is a factory interface, responsible for creating FieldElement which are scalars belonging to the field represented by this instance.

**Link**: [Field.java](pairings-api%2FField.java)

###### `FieldElement`

**Description**: An interface representing an element within a field, providing methods for basic arithmetic operations.

**Link**: [FieldElement.java](pairings-api%2FFieldElement.java)

###### `Group`

**Description**: This class provides methods to obtain elements belonging to the group represented by the instance.

**Link**: [Group.java](pairings-api%2FGroup.java)

###### `GroupElement`

**Description**: An interface representing an element within a group, providing methods for basic group operations.

**Link**: [GroupElement.java](pairings-api%2FGroupElement.java)

###### `PairingResult`

**Description**: An interface representing the result of a pairing operation, with methods to compare it to other group elements.

**Link**: [PairingResult.java](pairings-api%2FPairingResult.java)


## Test Plan
Since cryptographic code is often difficult to test due to code complexity and lack of a test oracle, we should design our test cases based on cryptographic properties that these implementations should satisfy.

Questions:
 * What are those properties? 
 * what is the adversary model (e.g., honest-but-curious, halting or malicious; static or adaptive) for this solution? How to validate for that?

Some ideas:
* [Cryptographic-Algorithm-Validation-Program](https://csrc.nist.gov/CSRC/media//Projects/Cryptographic-Algorithm-Validation-Program/documents/dss/ECDSAVS.pdf):
* https://csrc.nist.gov/CSRC/media/Events/lightweight-cryptography-workshop-2019/documents/papers/systematic-testing-of-lightweight-crypto-lwc2019.pdf


* Generation of Private and Public Key Pairs: Tests the generation of key pairs for correctness by having the IUT produce 10 key
  pairs. The private key provided is used to compute the public key, Q’. The computed value Q’ is then compared to the supplied public key, Q.
* Public Key Validation: Generate 12 key pairs for each supported curve, modify some of the public keys to introduce errors, and determine whether or not can detect these errors.
* Signature Generation: 10 preconfigured known messages per curve supported are supplied. Generate a signature for each of the messages. The message, public key, and signature components are returned. 
The signatures are compared against previous known signatures.
* Signature Verification: For each curve supported, 15 pseudorandom messages, a public key, Q, and a signature components (r, s) supplied, if some of the supplied values are modified so that signature verification should fail.


The proposal is to move forward with the implementation while at the same time creating a work group to discuss and collect ideas for testing and validating these features.

### Unit Tests
Some of the components that can be unit-tested: native-support. SPI loading and failing. JNI wrapping.
Limitations: native-support-library tests will need to be executed in different environments to provide a comprehensive code validation.

### Performance Testing
WIP

## Security Audit
TBD

## Implementation and Delivery Plan

1. Stage 1
    * Preconditions
      * A new repository
      * CI/CD pipelines to reference built artifacts in hedera-services
    * Implementation of the public interface for the TSS library
    * Implementation of the public API for Pairings API
    * Implementation of native-support library
    * Work in the test plan and validation
    * Enable a mock implementation for TSS library so it can be used on the platform side 
2. Stage 2
   * Preconditions
     * gradle multilanguage module with rust compilation plugin
   *  Implementation of Pairings API using JNI, arkworks, and alt-bn1238
   *  Implementation of EC-key utility.
3. Stage 3
   * Enable dynamic loading of Pairings API implementation using SPI.