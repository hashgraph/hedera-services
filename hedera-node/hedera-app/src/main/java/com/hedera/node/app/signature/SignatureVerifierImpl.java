package com.hedera.node.app.signature;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.ThresholdKey;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Future;

public class SignatureVerifierImpl implements SignatureVerifier {
    @Override
    public Future<Boolean> verifySignatures(Bytes signedBytes, List<SignaturePair> sigPairs, HederaKey key) {
        throw new UnsupportedOperationException("Not implemented yet!");
    }

    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signaturePairs,
            @NonNull final Key key) {

        return switch (key.key().kind()) {
            // You cannot have a Contract ID key as a required signer on a HAPI transaction
            case UNSET, DELEGATABLE_CONTRACT_ID, CONTRACT_ID -> Collections.emptyList();
            case ECDSA_384 -> collectSignature(signaturePairs, key.ecdsa384OrThrow());
            case ED25519 -> collectSignature(signaturePairs, key.ed25519OrThrow());
            case RSA_3072 -> collectSignature(signaturePairs, key.rsa3072OrThrow());
            case ECDSA_SECP256K1 -> collectSignature(signaturePairs, key.ecdsaSecp256k1OrThrow());
            case KEY_LIST -> collectSignatures(signaturePairs, key.keyListOrThrow());
            case THRESHOLD_KEY -> collectSignatures(signaturePairs, key.thresholdKeyOrThrow());
        };
    }

    @NonNull
    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final KeyList keyList) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one
        if (!keyList.hasKeys()) {
            // There are no keys in this key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        final var keys = keyList.keysOrThrow();
        final var collectedSignatures = new ArrayList<Bytes>(keys.size());
        for (final var key : keys) {
            // Every key in a KeyList must be valid, so if any of these keys have no associated signature,
            // then the KeyList as a whole is invalid, and we return an empty list. The KeyList might be
            // part of a ThresholdKey, so we don't want to presume it fails the whole transaction, but at least
            // it makes no sense to validate any signatures from this list.
            final var sigsToAdd = collectSignatures(signatures, key);
            if (sigsToAdd.isEmpty()) {
                return Collections.emptyList();
            }
            collectedSignatures.addAll(sigsToAdd);
        }
        return collectedSignatures;
    }

    @NonNull
    private List<Bytes> collectSignatures(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final ThresholdKey thresholdKey) {
        // Iterate over all the keys in the list, delegating to collectSignatures for each one,
        // and keep track of which signatures were successful and which weren't. The ones that
        // were not successful, we can ignore. If we do not have enough successful signatures,
        // then we throw the appropriate exception.
        if (!thresholdKey.hasKeys()) {
            // There is no key list, which means, nothing is authorized.
            return Collections.emptyList();
        }

        // Iterate over all the keys in the list, delegating to collectSignatures for each one. If they have
        // signatures, then we accumulate them and increase the `numSuccessfulKeys` by 1 (regardless of how many
        // signatures they gathered). At the end, if we have exceeded the threshold, then we return ALL of the
        // signatures we collected. This is really important.
        //
        // Suppose a threshold key is 2/4, meaning that 2 of the 4 keys must sign. Suppose the transaction has
        // 3 signatures on it from 3 of those 4 keys, but only 2 of them are valid signatures, the third being
        // some nonsense. The transaction should succeed. If we short-circuited this method after collecting 2 of 4
        // keys, we might have failed the transaction. So the specification should be that if it is possible to
        // select a set of signatures and keys that would succeed, then a valid consensus node implementation will
        // select those keys to succeed.
        var numSuccessfulKeys = 0;
        final var keyList = thresholdKey.keysOrThrow();
        final var keys = keyList.keysOrElse(Collections.emptyList());
        final var collectedSignatures = new ArrayList<Bytes>(keyList.keysOrThrow().size());
        for (final var key : keys) {
            final var sigs = collectSignatures(signatures, key);
            if (!sigs.isEmpty()) {
                collectedSignatures.addAll(sigs);
                numSuccessfulKeys++;
            }
        }

        // It should be impossible for the threshold to ever be non-positive. But if it were to ever happen,
        // we will treat it as though the threshold were 1. This allows the user to fix their problem and
        // set an appropriate threshold. Likewise, if the threshold is greater than the number of keys, then
        // we clamp to the number of keys. This also shouldn't be possible, but if it happens, we give the
        // user a chance to fix their account.
        var threshold = thresholdKey.threshold();
        if (threshold <= 0) threshold = 1;
        if (threshold > keys.size()) threshold = keys.size();

        // If we didn't meet the minimum threshold for signers, then we're bad.
        return (numSuccessfulKeys >= threshold) ? collectedSignatures : Collections.emptyList();
    }

    @NonNull
    private List<Bytes> collectSignature(
            @NonNull final List<SignaturePair> signatures,
            @NonNull final Bytes keyBytes) {

        for (final var signature : signatures) {
            if (keyBytes.matchesPrefix(signature.pubKeyPrefix())) {
                return List.of(signature.signature().as());
            }
        }
        return Collections.emptyList();
    }
}
