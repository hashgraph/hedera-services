package misc

import (
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"math/rand"
	"time"
)

const mintAmount uint64 = 1_000_000_000_000

func DoMints(client *hedera.Client) {
	simParams := LoadParams()
	simDetails := LoadDetails()

	i := 0
	for {
		MintRandomlyGiven(client, simDetails, simParams)

		fmt.Printf("💤 Sleeping %dms...\n\n", simParams.MillisBetweenMints)
		time.Sleep(time.Duration(simParams.MillisBetweenMints) * time.Millisecond)

		displayBalances := i > 0 && i%simParams.OpsBetweenBalanceDisplay == 0
		if displayBalances {
			DisplayBalances("LP", client, simDetails.LpIds, simDetails.TokenIds, simDetails.Tickers)
		}
		i++
	}
}

func MintRandomlyGiven(
	client *hedera.Client,
	simDetails details,
	simParams params,
) {
	numTickers := len(simDetails.Tickers)
	numLps := len(simDetails.LpIds)
	chosenLp := rand.Intn(numLps)
	lpId, err := hedera.ContractIDFromSolidityAddress(simDetails.LpIds[chosenLp])
	if err != nil {
		panic(err)
	}

	firstChoice := rand.Intn(numTickers)
	secondChoice := firstChoice
	for secondChoice == firstChoice {
		secondChoice = rand.Intn(numTickers)
	}
	tickerA := simDetails.Tickers[firstChoice]
	tickerB := simDetails.Tickers[secondChoice]

	tokenA, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[firstChoice])
	if err != nil {
		panic(err)
	}
	tokenB, err := hedera.ContractIDFromSolidityAddress(simDetails.TokenIds[secondChoice])
	if err != nil {
		panic(err)
	}

	fmt.Printf("🍵 LP %s looking to mint %s/%s\n", lpId, tickerA, tickerB)
	mintVia(client, tokenA, tokenB, mintAmount, mintAmount, lpId, simParams.CheckMintRecords, simParams.GasOfferPerMint)
}

func mintVia(
	client *hedera.Client,
	token0 hedera.ContractID,
	token1 hedera.ContractID,
	amount0 uint64,
	amount1 uint64,
	lpId hedera.ContractID,
	getRecord bool,
	gasToOffer uint64,
) {
	encAmount0 := Uint256From64(amount0)
	encAmount1 := Uint256From64(amount1)
	var mintParams = hedera.NewContractFunctionParameters()
	_, err := mintParams.AddAddress(token0.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = mintParams.AddAddress(token1.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	fmt.Print("  💸 Minting new liquidity position...")
	mintParams.
		AddUint256(encAmount0).
		AddUint256(encAmount1)
	if getRecord {
		var mintRecord hedera.TransactionRecord
		mintRecord, err = CallContractTentatively(client, lpId, "mintNewPosition", mintParams, gasToOffer)
		if err == nil {
			result, _ := mintRecord.GetContractExecuteResult()
			positionNftId := Uint64From256(result.GetUint256(0))
			liquidity := result.GetUint64(1)
			token0Used := result.GetUint64(2)
			token1Used := result.GetUint64(3)
			fmt.Printf("got position NFT %d, liquidity=%d (token0=%d, token1=%d)\n",
				positionNftId, liquidity, token0Used, token1Used)
		} else {
			fmt.Printf("%s\n", err)
		}
	} else {
		FireAndForget(client, lpId, "mintNewPosition", mintParams, gasToOffer)
		fmt.Print("OK\n")
	}
}
