package com.swirlds.platform.state.signed;

public record StateSavingResult(long round, boolean outOfBand) {
    public boolean inBand(){
        return !outOfBand;
    }
}
