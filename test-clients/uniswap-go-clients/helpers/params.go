package misc

import (
	"encoding/json"
	"io/ioutil"
)

type params struct {
	MillisBetweenSwaps       int      `json:"millisBetweenSwaps"`
	MillisBetweenMints       int      `json:"millisBetweenMints"`
	TokenNames               []string `json:"erc20TokenNames"`
	NumTraders               int      `json:"numTraders"`
	NumLiquidityProviders    int      `json:"numLiquidityProviders"`
	NumStartupMints          int      `json:"numStartupMints"`
	CheckSwapRecords         bool     `json:"checkSwapRecords"`
	CheckMintRecords         bool     `json:"checkMintRecords"`
	OpsBetweenBalanceDisplay int      `json:"opsBetweenBalanceDisplay"`
	GasOfferPerSwap          uint64   `json:"gasOfferPerSwap"`
	GasOfferPerMint          uint64   `json:"gasOfferPerMint"`
}

func LoadParams() params {
	rawParams, err := ioutil.ReadFile("./assets/params.json")
	if err != nil {
		panic(err)
	}
	simParams := params{}
	err = json.Unmarshal(rawParams, &simParams)
	if err != nil {
		panic(err)
	}
	return simParams
}
