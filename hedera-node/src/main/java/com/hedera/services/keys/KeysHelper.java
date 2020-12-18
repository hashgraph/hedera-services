package com.hedera.services.keys;

import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.codec.DecoderException;

public class KeysHelper {
    public static JKey ed25519ToJKey(ByteString prefix) throws DecoderException {
        var key = Key.newBuilder().setEd25519(prefix).build();
        return JKey.mapKey(key);
    }
}
