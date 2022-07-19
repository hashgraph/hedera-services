package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.util.Integers;
import org.apache.tuweni.bytes.Bytes;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_PAUSE_TOKEN;

class PausePrecompileTest {
    private final Bytes pretendArguments =
            Bytes.of(Integers.toBytes(ABI_PAUSE_TOKEN));
}
