# TSS-Library

## Summary

Provide necessary components for signing messages using a TSS scheme.

|      Metadata      |                        Entities                        |
|--------------------|--------------------------------------------------------|
| Designers          | Austin, Cody, Edward, Rohit, Maxi, <br/> Platform team |
| Functional Impacts | Platform team. Release engineering. DevOps             |
| Related Proposals  | TSS-Roster, TSS-Ledger-Id                              |
| Version            | 2                                                      |

## Purpose and Context

A threshold signature scheme (TSS) aims to enable a threshold number of participants (shareholders) to securely and efficiently generate succinct aggregate signatures
to cryptographically sign and verify signatures in the presence of some corruption threshold in a decentralized network.

In that scheme, a static public key is produced that doesn't change even when the number of participants in the scheme varies.
This is important for producing proofs that are easily consumable and verifiable by external entities.

Our implementation will be based on Groth21 [link]. This algorithm generates a public key and a private key for each participant.
The private key is distributed as key-splits (or shares of shares) such that no single party knows the private key.
Only a threshold number of key-splits can produce a valid signature that the aggregated public key can later verify.
It uses BLS signatures, Shamir's secret sharing, ElGamal, and Zero-Knowledge Proofs to achieve that goal.

This document covers the implementation of all necessary components to provide a generic library with the functionality to sign and verify
information using a Threshold Signature Scheme (TSS) and EC Cryptography.

This proposal assumes no relation with the platform and defines a generic component that any consumer can integrate.
It only assumes that there exists a channel to connect participants, where the identity of the message sender has been previously validated.
The process of sending messages through that channel and receiving the responses is also outside the scope of this proposal.
Additionally, participants will need access to each other's public key. While the generation of the public/private keys is included in this proposal,
the distribution aspect, the loading, and the in-memory interpretation from each participant are outside the scope of this proposal.
The related proposal, TSS-Ledger-Id, provides an overview of the process and background for TSS and how it impacts the platform’s functionality.

### Glossary

- **BLS (Boneh, Lynn, and Shacham) Signatures**: BLS signatures are a type of succinct signatures that allows aggregation.
- **TSS (Threshold Signature Scheme)**: A cryptographic signing scheme in which a minimum number of parties (reconstruction threshold) must collaborate
  to produce an aggregate signature that can be used to sign messages and an aggregate public key that can be used to verify that signature.
- **Groth 21**: Publicly verifiable secret sharing and resharing schemes that enable secure and efficient distribution and management of secret shares,
  with many possible use cases supporting applications in distributed key generation and threshold signatures.  As mentioned, it uses Shamir's secret sharing, ElGamal, and Zero-Knowledge Proofs.
- **Shamir’s Secret Sharing**: In Shamir’s SS, a secret `s` is divided into `n` shares by a dealer, and shares are sent to shareholders secretly.
  The secret `s` is shared among `n` shareholders in such a way that:
  (a) any party with at least `t` shares can recover the secret, and (b) any party with fewer than `t` shares cannot obtain the secret.
- **ElGamal**: On a message, the ElGamal signature scheme produces a signature consisting of two elements `(r, s)`, where `r` is a random number, and `s` is computed from the message, a signer's secret key, and `r`.
- **SNARK**: A proof system for proving arbitrary statements (circuits / programs).
- **Zero-Knowledge Proofs**: A proof system where one can prove possession of certain information, e.g., a secret key, without revealing that information or any interaction between the prover and verifier.
- **NIZK**: A non-interactive zero-knowledge proof for an statement. In TSS we use NIZK proofs for encoding the correctness of the secret sharing.
- **Elliptic Curve (EC)**: `Elliptic` is not elliptic in the sense of an `oval circle`. In the field `Fp`, an `EC` is like a non-connected cloud of points where
  all points satisfy an equation, and all operations are performed modulo `p`. Some elliptic curves are pairing-friendly.
- **Bilinear Pairings**: These are mathematical functions used in cryptography to map two elements of different groups (in EC, the group is an elliptic curve) to a single value in another group
  in a way that preserves specific algebraic properties.
- **Fields**: Mathematical structures where addition, subtraction, multiplication, and division are defined and behave as expected (excluding division by zero).
- **Groups**: Sets equipped with an operation (like addition or multiplication) that satisfies certain conditions (closure, associativity, identity element, and inverses).
- **Share**: Represents a piece of the necessary public/private elements to create signatures. Each share is in itself a valid Ec-Key.
- **Polynomial Commitment**: A process that enables evaluations of a polynomial at specific points to be verified without revealing the entire polynomial.
- **Participant**: Also share-holder, is any party involved in the distributed key generation protocol.
- **Participant Directory**: An address book of participants of the distributed key generation protocol.

### Goals

- **Usability**: Design user-friendly libraries with a public API that are easy to integrate with other projects, such as consensus node and other library users.
- **EVM support**: Generated signature and public keys should be compatible with EVM precompiled functions
  so that signature validation can be done on smart contracts without incurring an excessive gas cost.
- **Security**: Our produced code should be able to pass internal and external security audits.
- **Flexibility**: Minimize the impact of introducing support for other elliptic curves.
- **Independent Release**: When applicable, the new libraries should have the release cycle separate from the platform.

### Non-Goals

- Implement support for elliptic curve arithmetics in Java.
- Support any system/architecture other than: Windows amd64, Linux amd64 and arm64, and MacOS amd64 and arm64.
- Creation of the building artifacts and plugins for rust code.
- This proposal covers the implementation of a tool similar to ssh-keygen to generate those keys, but the generation, persistence, distribution
  and loading of those keys is outside the scope of this proposal.

## Changes

### Core Behaviors

The proposed TSS solution is based on Groth21.

Groth21 is a non-interactive, publicly verifiable secret-sharing scheme where a dealer can construct a Shamir secret sharing of a field element
and confidentially yet verifiably distribute shares to multiple receivers.
It includes a distributed resharing protocol that preserves the public key but creates a fresh secret sharing of the secret key and hands it to a set of receivers,
which may or may not overlap with the original set of shareholders.

### Overview

Participants can hold one or more shares, each of which can be used to sign a message.
The goal is to generate an aggregate signature which is valid if a threshold number of individual signatures are combined.

Each participant brings their own BLS key pair (private and public). They share their public keys with all other participants while securing their private keys.
Before the protocol begins, all participants agree on the cryptographic parameters (type of curve and what group of the pairing will be used for public keys and signatures).
When the protocol is initialized, a participant directory is built. This directory includes the number of participants, each participant’s BLS public key, and the shares they own.

Each participant generates portions of a secret share and distributes them among the other participants using the following process:

1. A random share is created and mathematically split into a known number key-splits (or share of shares).
   * Using Shamir's Secret Sharing and interpolation polynomials.
2. Each portion is encrypted with the share owner's public key, ensuring only the intended recipient can read it.
3. A message is created that includes all encrypted values so that only the intended recipients can decrypt their respective portions of the secret share.

(e.g.: for a directory of 10 participants and 10 shares distributing 1 share each with a threshold value of 6, `Participant 1` will generate a message out of a random key,
that will contain 10 portions of that key, each one encrypted under each participants' public key)
This setup allows participants to share secret information securely. The message also contains additional information necessary for its validation (Such as a polynomial commitment and a NIZK proof).

Upon receiving a threshold number of messages, each participant:

1. Validates the message and decrypts the information encrypted with their public key.
2. Aggregates the decrypted information to generate a private key for each owned share.
3. Retrieves a public key for each share in the system to validate signatures.

(e.g.: for a directory of 5 participants and 10 shares with a threshold value of 6 where Participant 1 has 2 shares;
`Participant 1` will collect at least 6 valid messages from all participants, take the first and second portions of each message, decrypt with it's BLS private key and aggregate them, so they become the first and second owned secret share)

Individual signing can then begin. Participants use the private information of their shares to sign messages.

An aggregate signature is created when signatures from at least the threshold number of parties are combined. This aggregate signature can be validated using the combined value of the public shares in the directory.

The process restarts whenever the number of participants or the shares assigned to each change. However, the initially generated group public key remains unchanged to maintain consistency.
New secret information for shares is created using existing data, ensuring the aggregate signature can still be verified with the original group public key.

### Architecture

To implement the functionality detailed in the previous section, the following code structure is proposed:

![img_5.png](img_5.svg)

1. **TSS Lib**: Used to create shares, create TSS messages to send to other nodes, assemble shared public keys, and sign messages.
2. **Pairings Signatures Library**: This library provides cryptographic objects (PrivateKey, PublicKey, and Signature) and operations to sign and verify signatures.
3. **Pairings API**: An API definition for the arithmetic operations required to work with a specific EC curves and the underlying Groups, Fields, and Pairings concepts. This API minimizes the impact of changing to different curves.
4. **Bilinear Pairings Impl**: An implementation of the Bilinear Pairings API that will be loaded at runtime using Java’s service provider (SPI) mechanism.
   Multiple implementations can be provided to support different types of curves and  the Java service provider approach facilitates easily changing between implementations and dependencies.
5. **Native Support Lib**: Provides a set of generic functions for loading native libraries in different system architectures when packaged in a jar, using a predefined organization so they can be accessed with JNI.
6. **EC-Key Utils** is a utility module that enables the node operator to generate a bootstrap public/private key pair.

### Module organization and repositories

![img_6.png](img_6.svg)
1. **hedera-cryptography**: This is a separate repository for hosting cryptography-related libraries.  It is necessary to facilitate our build process, which includes Rust libraries. It also provides independent release cycles between consensus node code and other library users.
2. **hedera-common-nativesupport**: Gradle module that enables loading into memory compiled native libraries so they can be used with JNI.
3. **hedera-cryptography-tss**: Gradle module for the TSS Library. This library's only client is the consensus node.
4. **hedera-cryptography-signatures**: Gradle module for the Bilinear Pairings Signature Library.
5. **hedera-cryptography-pairings-api**: Gradle module for the Bilinear Pairings API. Minimizes the impact of adding or removing implementations.
6. **hedera-cryptography-altbn128**: Gradle module that will implement the Bilinear Pairings API using alt-bn128 elliptic curve.

### Handling of multilanguage modules and native code distribution

The software provided by this approach will require multi-language modules (rust + java). Rust code must be compiled into binary libraries and accessed through JNI.
Given the immature state of development, Project Panama is not considered for this proposal; but Project Panama may be considered for future development.

There are two possible ways of loading libraries and accessing them through JNI
1) Libraries are installed on the system as shared object(SO) libraries and found via the classpath and system library path.
2) Distributed with the application jars, unpacked, and loaded at runtime.

We want to ensure that dependent software does not require the installation of any additional dependencies other than those distributed in the jars, so we are choosing option 2.
We will provide a library `hedera-common-nativesupport` that will help load and use binary library and binding with JNI.
The low-level details of the build logic for this to work are outside the scope of this proposal.
A high overview is mentioned for the benefit of readers.

In multilanguage projects, Rust code will be placed in a structure such as:

```
hedera-multilanguage-project
    ├── main
    │   ├── java
    │   │   └── **
    │   └── rust
    │       └── **
    ├── test
    │   └── java
    │       └── **
    ├── cargo.toml
    └── build.gradle.kts
```

Then it will be cross-compiled into a set of binary libraries and then packaged in the same jar as the java code accessing it.

Rust code will be compiled first and the build process will create the following folder structure where binaries files will be placed and then distributed.
They will be arranged by platform identifier, as returned by `System.getProperty("os.name")` and `System.getProperty("os.arch")`.

```
/software
    ├── darwin
    │   ├── amd64
    │   │   └── native_lib.dylib
    │   └── arm64
    │       └── native_lib.dylib
    ├── linux
    │   ├── amd64
    │   │   └── native_lib.so
    │   └── arm64
    │       └── native_lib.so
    └── windows
        └── amd64
            └── native_lib.dll
```

This whole process will be produced both locally and in CI/CD.

### Libraries Specifications

#### Hedera Cryptography Pairings API

##### Overview

This API will expose general arithmetic operations to work with elliptic curves and billinear pairings that implementations must provide.

##### Public Api

![img_7.svg](img_7.svg)

###### `Curve`

An interface that represents the different types of elliptic curves.

###### `PairingFriendlyCurve`

This class provides access to each group (G₁, G₂) enabled for pairings operations and the scalar field associated with the curve.
It provides an initialization method to load all necessary dependencies for the library to work. Callers are requested to invoke that method before start using the library.
Implementations of this API should decide if they provide any, one, or many curves.

###### `Field`

Represents a finite field used in a pairing-based cryptography scheme.
A finite field is a ring of scalars values [0,1,...r-1, 0, 1, ...] where all operations are done modulus a prime number P.
All operations inside the field, produce a value that belongs to it.

Each scalar element in the field is a `FieldElement`.
Supports the following operations:
- create an element out of its serialized form
- create the zero element
- create the one element
- create a random element

###### `FieldElement`

A scalar value that is member of `Field`. In an elliptic curve, these are the values that can be used to operate in certain `Group` operations.
Supports the following operations:
- addition
- subtraction
- power
- multiplication
- serialization
- inversion

###### `Group`

Represents a mathematical group used in a pairing-based cryptography system.
A group in this context is a set of elements (curve points) with operations that satisfies the group properties:
* closure
* associativity
* identity
* invertibility

Curves can be defined by more than one group.
This class provides methods to obtain elements belonging to the group represented by the instance and some operations that handle multiple points.
- create a point out of its serialized form
- create a random point
- (hashToGroup) create a point out of a hashed (sha256) value of a byte array
- create the zero point or point at infinity
- retrieve the group generator point
- add a list of points and return the aggregate result
- multiply the generator point with a list of scalars and return each result

###### `GroupElement`

Represents an element of `Group`. In an elliptic curve, these are points.
- addition
- multiplication for an scalar
- serialization

###### `BillinearPairing`

Represents a bilinear pairing operation used in cryptographic protocols.
A pairing is a map: e : G₁ × G₂ -> Gₜ which can satisfy these properties:
* Bilinearity: `a`, `b` member of `Fq` (Finite Field a.k.a. `Field`), `P` member of `G₁`, and `Q` member of `G₂`,
then `e(a×P, b×Q) = e(ab×P, Q) = e(P, ab×Q) = e(P, Q)^(ab)`
* Non-degeneracy: `e != 1`
* Computability: There should be an efficient way to compute `e`.

#### Hedera Cryptography alt-bn128

##### Overview

Implementation module of the parings API for `alt-bn128` (also known as `bn254`) pairings friendly curve. That curve has been chosen due to the existence of EVM precompiles that allows to do this verification in a cheap way for smart contract users.
The underlying elliptic curve and fields algebra operations and primitives will be implemented using `Rust` `arkwrorks` library, accessed through a JNI interface.
The module will include Java and Rust code that will be compiled for all supported system architectures and distributed in a jar with a predefined structure.

##### Implementation details

`Alt-bn128` curve uses a Scalar field based on the prime number
* r=21888242871839275222246405745257275088548364400416034343698204186575808495617
* p=21888242871839275222246405745257275088548364400416034343698204186575808495617
* q=21888242871839275222246405745257275088696311157297823662689037894645226208583
* Generator 5.
* G1 generator: (1, 2)
* G2 generator: (10857046999023057135944570762232829481370756359578518086990519993285655852781,  11559732032986387107991004021392285783925812861821192530917403151452391805634,
8495653923123431417604973247489272438418190587263600148770280649306958101930, 4082367875863433681332203403145435568316851327593401208105741076214120093531)

It is defined by two groups `AltBn128Group1` and `AltBn128Group2`.
- Serialized forms of elements from `AltBn128Group1` (`AltBn128Group1Element`) are 64 bytes byte arrays in little endian format, separated in 32 bytes for `x` and 32 for `y`.
- Serialized forms of elements from `AltBn128Group2` (`AltBn128Group2Element`) are 128 bytes byte arrays in little endian format, separated in 2 bytes arrays of 32 bytes each for `x` and 2 bytearrays of 32 bytes each for `y`.

This library will not have a public api as it is intended to be used only as runtime dependency.

###### Encoding:

`FieldElements` scalars are encoded as 32 byte little-endian numbers.
Curve points are encoded as two components (x, y) that differs in size for each group of the curve.
Group 1 `GroupElements` have the form (X, Y) each 32 little-endian bytes numbers.
Group 2 `GroupElements` have the form (X₁, X₂, Y₁, Y₂) each 32 little-endian bytes numbers.

The point at infinity in all cases is stored a 32-bytes 0 value in each component.

###### Code Organization Structure:

```
hedera-cryptography-altbn128
    ├── main
    │   ├── java
    │   │   └── **
    │   └── rust
    │       └── **
    └── test
        └── java
            └── **
```

#### Hedera Cryptography Pairings Signatures Library

##### Overview

This provides Public Keys, Private Keys, and Signatures and operations to produce each other.

##### Public API

###### `SignatureSchema`

A pairings signature scheme can be implemented with different types of curves and group assignment configurations.
For example, two different configurations might consist of a `BLS_12_381` curve using short signatures or short public keys
(which translates to choosing `G₁` of the curve to generate public key elements or `G₂` for the same purpose).
This class allows to specify those parameters internally, so users of the library don't need to keep track of the specific parameters that were used to produce the keys or signatures.

###### `PairingsPrivateKey`

A private key generated using the pairings API. It is a Random `FieldElement` out of a 32 bytes seed.
This class provides the following operations:
- create a public key: It is the scalar multiplication of the private key and the `generator` `GroupElement` of the `Group` configured in the `SignatureSchema` for public keys.
- sign a message: It is the scalar multiplication of the private key element, and the `hashToGroup` `GroupElement` from the message, of the `Group` configured in the `SignatureSchema` for signatures.

###### `PairingsPublicKey`

A public key generated using the pairings API. Under the hood it is a `GroupElement` meaning that is a point in the selected curve.

###### `PairingsSignature`

A signature generated with the private key that can be verified with the public key. Under the hood it is a `GroupElement` meaning that is a point in the selected curve.
- verify against a public key and the original signed message: This operation uses the pairings operation and verifies that:

```
pairing.pairingBetween(signatureElement, publicKeyGroup.getGenerator());
==
pairing.pairingBetween(messageHashedToGroup, publicKey.keyElement());
```

###### `ElGamalUtil`

A utility class that helps to encrypt a decrypt using ElGamal method.
Operations:
- Decrypt a message:
- ElGamal encrypt:

```
var r =  Field.randomFieldElement(random);
var c1 = this.groupElement().group().generator().mul(&r);
var c2 = this.groupElement().mul(&r).add(this.groupElement().group().generator().generator.mul(&m));
return { c1, c2 }
```

##### Serialization Note

Consumers of this library should not need to understand the elements as points on the curve. It is sufficient for them to interact with an opaque representation.
To decouple the keys and signatures from the api, internal structure of the signatures and the keys should be stored in byte arrays or a serializable structure.

Two possibilities :
1. **Use arkworks-based serialization mechanism**
This serialization mechanism uses the encoding defined for the `parings-api` and its ability to produce byte arrays from its components.
Given that we have the guarantee to be canonical, and that we do not plan for internal java consumers to need to operate with the individual values.
Pros:
- decouples the serialization mechanism from the pairings internal structure and allows to switch curves with 0 impact on the consumers
- It allows consumers that have no access or cannot use serialization libraries such as protobuf
- Simple and reduced effort, low infra support needed.
Cons:
- It is coupled to arkworks serialization mecanism
- The communication needs to happen verbally or through documents and there is no technical interface we can share.

2. **Use Protobuf serialization**
   This serialization mechanism consist on generating a structure that represents the internal representation of PublicKey, PrivateKey, Signature in a defined protobuf schema.
   We would need to extract the information from the pairings api implementation and produce a protobuf object to expose.
   Pros:
   - Allows to share a schema to know how to interpret the curve
   - Any client that can use protobuf can interpret our elements
     Cons:
   - Only clients that can use protobuf can interpret signatures, and public keys
   - By producing a structure for the internals of the pairings api, new curves would need new structures.

#### Hedera Cryptography TSS Library

##### Overview

This library implements the Groth21 TSS-specific operations.

##### Public Interface

###### `TssMessage`

A data structure for distributing encrypted shares of a secret among all participants in a way that only the intended participant can see its part of the share.
It includes auxiliary information used to validate its correctness and assemble an aggregate public key, i.e., a commitment to a secret share polynomial and a NIZK proof.

###### `TssShareId`

A deterministic unique, contiguous starting from 1 identifier for each existent share.
a) are unique per share,
b) non-0, and
c) can be used as input for the polynomial (They are from the same field of the selected curve)

###### `TssPrivateShare`

Represents a share owned by the executor of the scheme. Contains a secret value used for signing. It is also a ECPrivateKey.

###### `TssPublicShare`

Represents a share in the system. It contains public information that can be used to validate each signature. It is also a ECPublicKey

###### `TssShareSignature`

Represents a signature created from a TSSPrivateShare.

###### `TssParticipantDirectory`

This class holds all information about the participants in the scheme. Including: participants' EC public keys, public shares, private shares, number of shares.

###### `TssService`

Class that implements all the TSS operations allowing:
* Generate TSSMessages out of a random PrivateShare
* Generate TSSMessages out of a list of PrivateShares
* Verify TSSMessages out of a ParticipantDirectory
* Obtain PrivateShares out of TssMessages for each owned share
* Aggregate PrivateShares</li>
* Obtain PublicShares out of TssMessages for each share</li>
* Aggregate PublicShares</li>
* sign Messages</li>
* verify Signatures</li>
* Aggregate Signatures</li>

##### Usage

###### Input

* Participant's persistent EC Private key (Private to each participant)
* Number of participants (Public)
* Number of shares per participant (Public)
* A threshold value externally provided:`e.g: t = 5`
* All participants' persistent EC public keys (Public)
* A predefined `SignatureSchema` (Public / Constant for all the network)

###### 0. Bootstrap

Given a participants directory, e.g:

```
P   #   TssEncryptionPublicKey
-----------------------------
P₁  5   P₁_TssEncryptionPublicKey
P₂  2   P₂_TssEncryptionPublicKey
P₃  1   P₃_TssEncryptionPublicKey
P₄  2   P₄_TssEncryptionPublicKey

```

```java
//Given:
TssService service = new TssService(signatureScheme, new Random());
PairingPrivateKey tssEncryptionPrivateKey = loadCurrentParticipantTssEncryptionPrivateKey();
List<PairingPublicKey> tssEncryptionPublicKeys = loadAllParticipantsTssEncryptionPublicKeys();

//Then:
TssParticipantDirectory participantDirectory =
        TssParticipantDirectory.createBuilder()
        .self(/*Identification, different for each participant*/ 0, tssEncryptionPrivateKey)
        .withParticipant(/*Identification:*/0, /*Number of Shares*/5, tssEncryptionPublicKeys.get(0))
        .withParticipant(/*Identification:*/1, /*Number of Shares*/2, tssEncryptionPublicKeys.get(1))
        .withParticipant(/*Identification:*/2, /*Number of Shares*/1, tssEncryptionPublicKeys.get(2))
        .withParticipant(/*Identification:*/3, /*Number of Shares*/1, tssEncryptionPublicKeys.get(3))
        .withThreshold(5)
       .build(signatureScheme);

//After:
//One can then query the directory
int n = participantDirectory.getTotalNumberOfShares();
List<TssShareId> privateShares = participantDirectory.getOwnedSharesIds();
List<TssShareId> shareIds = participantDirectory.getShareIds();
```

Under the hood, a `shareId`: `sid` is generated for each share.
And an ownership map is maintained inside the directory: `ShareId`->`Participant`, e.g:

```
sid₁	sid₂	sid₃	sid₄	sid₅	sid₆	sid₇	sid₈	sid₉	sid₁₀
P₁  	P₁  	P₁  	P₁  	P₁  	P₂  	P₂  	P₃  	P₄  	P₄
```

###### 1. Create TssMessage

```java
//Creates a TssMessage out of a randomly generated share
TssMessage message = service.generateTssMessage(participantDirectory);
```

**Implementation details**
Internally the process of creating a TssMessage consist of

a. Generation of the shares
In this operation for the bootstrap process, a random  `TssPrivateShare` `k` is created. It is the same process as creating a random ECPrivateKey.
After that, the key is split into `n` (n=total number of shares) values `Xₛ` by evaluating a polynomial Xₖ at each `ShareId`: `sidᵢ` in the ownership map.
The polynomial `Xₖ` is a polynomial with degree `t-1` (t=threshold) with the form:
`Xₖ = k + a₁x + ...aₜ₋₁xᵗ⁻¹`[ having: `a₁...aₜ₋₁`: random coefficients from `SignatureScheme.publicKeyGroup` and `k`'s EC field element. x is a field element, thus allowing the polynomial to be evaluated for each share id]
Each `sᵢ = Xₖ(sidᵢ)` constitutes a point on the polynomial.
Once the `sᵢ` value has been calculated for each `ShareId`: `sidᵢ`, the value: `Cᵢ` will be produced by encrypting the `sᵢ` using the `sidᵢ` owner's public key.
The TssMessage will contain all the encrypted values for all shares.
![img.svg](img.svg)

b. Generation of the Polynomial Commitment
We include a Feldman commitment to the polynomial as a mechanism to detect forms of bad dealing.
For each coefficient in the polynomial `Xₖ` `a₍ₒ₎` to `a₍ₜ₋₁₎`, compute a commitment value by calculating: `gᵢ * aᵢ ` (g multiplied by polynomial coefficient `a₍ᵢ₎` )

c. Generation of the NIZKs proofs
Generate a NIZK proof that these commitments and the encrypted shares correspond to a valid sharing of the secret according to the polynomial.

###### Acting as dealers of `TssMessage`s (_outside the scope of the library_)

Using an established channel, each participant will broadcast a single message to be received by all participants
while waiting to receive other participants' messages. This functionality is critical for the protocol to work but needs to be handled outside the library.
Each participant will validate the received message against the commitment and the NIZKs proof.
Invalid messages need to be discarded.
`TssMessage`s can be serialized to bytes.

###### 2. Validation of TssMessage

```java
static {
    //given
    List<TssMessage> messages = List.of(someTssMessage, someOtherTssMessage);
    //then
    for(TssMessage m : messages){
        if(!service.verifyTssMessage(participantDirectory, m))
            throw new SomeException("There are non valid tssMessages");
    }
}
```

The validation is produced over the content of the message and does not include the sender's identity, which is assumed to be provided by the external channel.
Each message can be validated against the commitment and the proof by:
* Checking that the encrypted shares correspond to the commitments.
* and, that the commitments are consistent with the public values and the generated proof.

###### 3. Generating Participant's Private Shares & Ledger Id

```java
// Given some previously agreed upon same set of valid messages for all participants
Set<TssMessage> agreedValidMessages = Set.of(someTssMessage, someOtherTssMessage, etc);
//Get Private Shares
List<TssPrivateShare> privateShares = service.decryptPrivateShares(participantDirectory, agreedValidMessages);
//Get Public Shares
List<TssPublicShare> publicShares = service.computePublicShare(participantDirectory, agreedValidMessages);
```

Given Participant's `TssEncryptionPrivateKey` and precisely `t` number of validated messages (t=threshold)
The participant will decrypt all `Cᵢ` to generate an aggregated value `sᵢ` that will become a  `SecretShare(sidᵢ, sᵢ)` for each `ShareId`: `sidᵢ` owned.

**Note:** All participants must choose the same set of valid `TssMessages` and have a threshold number of them.

![img_1.svg](img_1.svg)

Also, we will extract a `PublicShare` for each `ShareId`: `sidᵢ` in the directory from the list of valid messages.
The PublicShare for share `s` is computed by evaluating each polynomial commitment in the common set of messages at `sidᵢ` and then aggregating the results.

###### 4. Sign and aggregate/validate signatures

At this point, the participant executing the scheme can start signing, sharing signatures, and validating individual signatures produced by other parties in the scheme.
Using each `privateShares` owned by the participant, a message can be signed, producing a `TssShareSignature`
a. Perform signing

```java
//Given
byte[] message = getMessageToSign();
//Then
List<TssShareSignature> signatures = service.sign(privateShares, message);
```

Again, it is outside the scope of this library the action of distributing or collecting other's participants signatures.

Multiple `TssShareSignature` can be aggregated to create an aggregate `EcSignature`. An aggregate `PairingSignature` can be validated against the LedgerId (public key)  if
`t` (t=threshold) valid signatures are aggregated. If the threshold is met and the signature is valid, the library will respond with true; if not, it will respond with false.

```java
static {
    //Given
    List<TssShareSignature> signatures = receiveParticipantsSignatures();

    //Then: validation of individual signatures
    List<TssShareSignature> validSignatures = new ArrayList<>(signatures.size());
    for (TssShareSignature signature : signatures) {
        if (service.verifySignature(participantDirectory, publicShares, signature)) {
            validSignatures.add(signature);
        }
    }

    //Producing an aggregate signature
    PairingSignature aggregateSignature = service.aggregateSignatures(validSignatures);
}
```

###### 5. Rekey Stage

The rekey process should be executed each time some of the scheme’s parameters change, such as the number of participants or the number of shares assigned to each participant.
This rekeying process may also be conducted for reasons other than parameter changes; to rotate shares, for example.

```java
//Given
List<TssPrivateShare> privateShares = List.of(firstOwnedPrivateShare, secondOwnedPrivateShare, etc);
PairingPrivateKey tssEncryptionPrivateKey = loadCurrentParticipantTssEncryptionPrivateKey();
List<PairingPublicKey> tssEncryptionPublicKeys = loadAllParticipantsTssEncryptionPublicKeys();
TssParticipantDirectory oldParticipantDirectory = getPreviousParticipantDirectory();
int newThreshold = someNewThreshold;

//And:
TssParticipantDirectory newParticipantDirectory =
        TssParticipantDirectory.createBuilder()
                .self(/*Identification, different for each participant*/ 0, tssEncryptionPrivateKey)
                .withParticipant(/*Identification:*/0, /*Number of Shares*/3, tssEncryptionPublicKeys.get(0))
                .withParticipant(/*Identification:*/1, /*Number of Shares*/2, tssEncryptionPublicKeys.get(1))
                .withParticipant(/*Identification:*/2, /*Number of Shares*/1, tssEncryptionPublicKeys.get(2))
                .withParticipant(/*Identification:*/3, /*Number of Shares*/1, tssEncryptionPublicKeys.get(3))
                .withParticipant(/*Identification:*/4, /*Number of Shares*/3, tssEncryptionPublicKeys.get(3))
                .withParticipant(/*Identification:*/5, /*Number of Shares*/2, tssEncryptionPublicKeys.get(3))
                .withThreshold(newThreshold)
                .withPreviousThreshold(oldParticipantDirectory.threshold())
                .build(newThreshold);

//Creates a TssMessage out of a randomly generated share
TssMessage message = service.generateTssMessages(participantDirectory, privateShares);
```

The rekeying process is similar to the bootstrap process, but it starts with the previous list of owned private `SecretShare`.
The main difference with the genesis stage is that every participant generates a `TssMessage` out of each previously owned `SecretShare`.

![img_4.svg](img_4.svg)

Once finished, the list of `SecretShare`s will be updated but the previously generated aggregate public key remains the same.

##### Security Considerations

As long as an adversary Participant knows fewer than a threshold number of decryption keys, they cannot recover enough information to start forging threshold signatures.
Adversarial participants may learn the shares of the participants whose keys they have compromised, but more is needed to recover the secret.
For security, adversarial parties might choose low or zero entropy values for protocol inputs such as shareIds, but here will still be sufficient entropy in the overall protocol from honest nodes.

#### Hedera Common Native Support

##### Overview

This library provides classes that assist other modules to load native libraries packaged in dependency jars.

##### Constraints

This module will not depend on hedera-services artifacts, so it cannot include logging, metrics, configuration, or any other helper module from that repo.

##### Public API

###### `OperatingSystem`

An enum class listing all supported OS.

###### `Architecture`

An enum class listing all supported architectures.

###### `NativeLibrary`

Helper class that will load a library for the current O.S/Architecture

###### `SingletonLoader`

Helper class that will load a library for the current O.S/Architecture

## Test Plan

Refer to: [TSS-Library-Test-Plan.md](TSS-Library-Test-Plan.md)

### Performance Testing

JMH benchmarks should be provided for signature generation and aggregation.
TssMessage validation, and public key aggregation.

We should know that variance (min, max, average, median) time it takes to do the following:

Genesis for a 4, 10, 20, 40, and 100 participants.
Rekeying for a 4, 10, 20, 40, and 100 participants.
Shares Amounts distributed randomly: 200, 500, 1000, 2000, 5000
we need to test each configuration 100 times to get confident mins, maxes, and a good average and median

## Security Audit

After this proposal is accepted we will invite security team to define the necessary steps for auditing the code.

## Alternatives Considered

- As mentioned before, Java Foreign Function Interface is still immature so it was not considered for this proposal.
- JRA was discarded given the possibility of eventually moving to JFF.
- Using JSA through Bouncy Castle implementation was analyzed. BC provides support for EC-Curves, but it does not support Pairings which are required for BLS signatures.
  [beacon-chain](https://github.com/harmony-dev/beacon-chain-java/tree/master) implemented the support using bouncy castle + [Milagro](https://incubator.apache.org/projects/milagro.html)
  but Milagro project is reported to have little coverage, not audited. Milagro podling has been retired on 2024-03-21.

## Implementation and Delivery Plan by stages

**Stage 1**
* Preconditions:
1. hedera-cryptography repository.
2. Gradle multilanguage module with rust compilation plugin.
3. CI/CD pipelines for building artifacts in hedera-cryptography.
* Define the Test plan.
* Define a security plan.
* Implementation of nativesupport library.
* Implementation of Pairings API using JNI, arkworks, and alt-bn128.
* Implementation of Pairings Signatures library.
* Implementation of TSS library public interface (TBD: include mock implementation).
* Execute Test Plan and validation.
* Execute Security Audits.
* Implementation of EC-key utility.

**Stage 2**
* Preconditions:
1. CI/CD pipelines to reference built artifacts in hedera-cryptography from hedera-services.
2. Implementation of the public interface for the TSS library.
* Implementation of TSS library.
* Execute Test Plan and validation.
* Execute Security Audits.

## External References

- https://eprint.iacr.org/2021/339
- https://crypto.stanford.edu/pbc/notes/elliptic
- https://andrea.corbellini.name/2015/05/17/elliptic-curve-cryptography-a-gentle-introduction/
- https://www.iacr.org/archive/asiacrypt2001/22480516.pdf
- https://hackmd.io/@benjaminion/bls12-381#Motivation
- https://www.johannes-bauer.com/compsci/ecc/
