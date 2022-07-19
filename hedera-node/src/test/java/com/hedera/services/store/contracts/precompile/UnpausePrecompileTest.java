package com.hedera.services.store.contracts.precompile;

import com.esaulpaugh.headlong.util.Integers;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hedera.services.store.contracts.precompile.AbiConstants.ABI_UNPAUSE_TOKEN;

@ExtendWith(MockitoExtension.class)
public class UnpausePrecompileTest {
    private final Bytes pretendArguments =
            Bytes.of(Integers.toBytes(ABI_UNPAUSE_TOKEN));
}
