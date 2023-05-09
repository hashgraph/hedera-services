# Signature Verification

Transactions sent to the network are _signed_ by one or more cryptographic keys to assert authorization for the
transaction. The signature is a cryptographic proof that the transaction was authorized by the owner of the
cryptographic key. Hedera supports ED25519 and ECDSA(secp256k1) cryptographic keys. In addition, Hedera supports
multiple signature keys, known as _multi-sig_. Multi-sig keys are a collection of keys that are required to sign a
transaction. Hedera supports both the `KeyList` and `ThresholdKey` multi-sig key types. A `KeyList` is a collection of
keys, all of which must sign to "activate" the key. A `ThresholdKey` is a collection of keys, a minimum number of which
must sign to "activate" the key. Both `KeyList` and  `ThresholdKey` can be nested, allowing for arbitrarily complex
multi-sig schemes.

When transactions are sent to the network, they contain the signatures, and they contain the signed bytes. But that
alone is not sufficient information to know which signature applies to which key. For this reason, the transaction
doesn't just contain the signatures, but actually a "signature map", or _sigmap_ for short. This map is actually
represented in the protobuf as a list of `SignaturePair` objects. Each `SignaturePair` contains a public key prefix,
the type of key, and the signature. When authenticating a transaction, the network will look for the signature that
best matches a cryptographic key that may have be used to sign the transaction, and then verifies the correctness of
that signature.

The sender may also send additional signatures beyond those required to authorize the transaction. In most cases, these
are meaningless, and cost the _payer_ for no benefit. However, some services such as the Schedule Service do benefit
from this feature.

Signature verification is broken down in the following steps:
1. Based on the transaction and the current state of the system, determine the set of keys that are required to sign
   the transaction.
2. Given a _sigmap_ a set of keys, _expand_ the `SignaturePairs` from prefixes to full keys, filtering out any
   signatures that don't have a corresponding key.
3. Verify the signatures and keys are valid for the given transaction bytes

Each of these steps is executed as part of the pre-handle workflow. Signature checking is

![sig-expansion.drawio.png](..%2F..%2Fassets%2Fsig-expansion.drawio.png)

        // NOTE: One of the main reasons we have the pre-handle phase is to asynchronously verify signatures.
        // Signature verification itself is computationally intense, so using multiple background threads makes sense.
        // The payer pays for every signature included in the signature map whether it is verified or not.
        //
        // Each transaction has a "signature map", which is a list of "SignaturePair"s, where each individual
        // "SignaturePair" is a combination of a "key prefix" and a signature. The "key prefix" can be any number of
        // bytes from the public key associated with the signature. If there are no bytes, then the signature may
        // apply to any key. Or it may contain the entire 32/33 public key bytes (depend on the type of key, it may be
        // 32 or 33 bytes). Of it may be some minimal set of prefix bytes.
        //
        // It may be that more signatures are included in the transaction than are actually needed, or it may be that
        // fewer signatures are provided than are needed, or it may be that some of the signatures provided are not
        // valid.
        //
        // Keys come in two kinds: "primitive" cryptographic keys (e.g. ED25519, ECDSA_SECP256K1), and composite
        // keys (KeyList, ThresholdKey). Signatures are only of the cryptographic variety, while keys like an account
        // admin key or burn key may be cryptographic or composite keys. If we have a payer key, and it is a composite
        // key, then we may need multiple signatures on the transaction for the payer key to be "activated".
        //
        // To verify a key, we need to determine which signature pairs are required to verify the key. If we have got
        // to this point in pre-handle, we know we need to verify the payer key. We may have additional non-payer keys
        // that are required and need to be verified. Currently, some services have keys that are either optional
        // or may be required but not known beforehand. Because of this, we cannot *only* verify signatures tied to
        // known required keys. Rather, we need to verify *every* signature that we can match to a key. If the
        // signature's "prefix" is a full key, then we will verify that signature as well.
        //
        // Our goal is to verify every signature we can during pre-handle, even if it doesn't end up being used during
        // handle. The payer is paying for every signature, and checking it on multiple threads before we get to the
        // handle thread is preferable. Since the payer is paying for it, no wise payer would intentionally waste hbar
        // asking us to verify unnecessary signatures, and no unwise payer can hurt the network by causing us to do work
        // that isn't paid for.