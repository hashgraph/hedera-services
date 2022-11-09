package com.hedera.services.api.implementation;

import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;

public record ServicesAccessor(
        ServicesContext<CryptoService> cryptoService,
        ServicesContext<FileService> fileService,
        ServicesContext<TokenService> tokenService) {
}
