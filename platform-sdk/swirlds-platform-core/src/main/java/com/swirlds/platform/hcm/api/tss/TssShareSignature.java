package com.swirlds.platform.hcm.api.tss;

import com.swirlds.platform.hcm.api.signaturescheme.PairingSignature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a signature created by a TSS share.
 *
 * @param shareId   the share ID
 * @param signature the signature
 */
public record TssShareSignature(@NonNull TssShareId shareId, @NonNull PairingSignature signature) {
}
