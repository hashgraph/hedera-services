package misc

import (
	"encoding/binary"
	"encoding/json"
	"fmt"
	"github.com/hashgraph/hedera-sdk-go/v2"
	"io/ioutil"
	"strings"
)

const poolFee = 500
// NOTE: this is actually hard-coded to 79228162514264337594000000000 in the pool bytecode
const initSqrtPriceX96 uint64 = 1
const initTokenSupply uint64 = 1_000_000_000_000_000_000
const initLpTokenBalance uint64 = 1_000_000_000_000_000
const initTraderTokenBalance uint64 = 1_000_000_000_000

func SetupSimFromParams(client *hedera.Client) {
	simParams := LoadParams()

	fmt.Printf(`Using simulation params,
  ERC20 tokens : %s
  # traders    : %d
  # LPs        : %d%s`,
		strings.Join(simParams.TokenNames, ", "),
		simParams.NumTraders,
		simParams.NumLiquidityProviders,
		"\n\n")

	weth9Id := createWETH9(client)
	posDescId := createNftPositionDescriptor(weth9Id, client)
	factoryId := createFactory(client)
	posManagerId := createPositionManager(weth9Id, factoryId, posDescId, client)
	routerId := createRouter(weth9Id, factoryId, client)

	erc20InitcodeId := UploadInitcode(client, "./assets/bytecode/NamedERC20.bin")
	fmt.Printf("💰 ERC20 initcode is at file %s\n", erc20InitcodeId.String())
	var tickers []string
	var tokenIds []string
	var typedTokenIds []hedera.ContractID
	for _, name := range simParams.TokenNames {
		symbol := symbolFor(name)
		var consParams = hedera.NewContractFunctionParameters().
			AddString(name).
			AddString(symbol).
			AddUint64(initTokenSupply)
		erc20Id := createContractVia(client, erc20InitcodeId, consParams)
		tickers = append(tickers, symbol)
		tokenIds = append(tokenIds, erc20Id.ToSolidityAddress())
		typedTokenIds = append(typedTokenIds, erc20Id)
		fmt.Printf("  -> Token '%s' (%s) deployed to %s\n", name, symbol, erc20Id.String())
	}

	fmt.Println("\nNow creating pairs...")
	for i, a := range typedTokenIds {
		for j, b := range typedTokenIds[(i + 1):] {
			k := i + j + 1
			var deployParams = hedera.NewContractFunctionParameters()
			_, err := deployParams.AddAddress(a.ToSolidityAddress())
			if err != nil {
				panic(err)
			}
			_, err = deployParams.AddAddress(b.ToSolidityAddress())
			if err != nil {
				panic(err)
			}
			deployParams.AddUint32(poolFee)
			record := CallContractVia(client, factoryId, "createPool", deployParams)
			result, err := record.Children[0].GetContractCreateResult()
			if err != nil {
				panic(err)
			}
			fmt.Printf("  ☑️  Created %s/%s pair @ %s (Hedera id %s)...",
				tickers[i],
				tickers[k],
				result.EvmAddress.String(),
				record.Children[0].Receipt.ContractID.String())

			var initParams = hedera.NewContractFunctionParameters().AddUint64(initSqrtPriceX96)
			initRecord := CallContractVia(client, result.EvmAddress, "initialize", initParams)
			fmt.Printf("initialization at 1:1 price returned %s\n", initRecord.Receipt.Status)
		}
	}

	fmt.Println("\nNow creating LPs...")
	lpInitcodeId := UploadInitcode(client, "./assets/bytecode/TypicalV3LP.bin")
	fmt.Printf("LP initcode is at file %s\n", lpInitcodeId.String())
	var lpIds []string
	for i := simParams.NumLiquidityProviders; i > 0; i-- {
		nextId := createLp(weth9Id, factoryId, posManagerId, lpInitcodeId, client)
		lpIds = append(lpIds, nextId.ToSolidityAddress())
		fundAccountVia(client, initLpTokenBalance, typedTokenIds, nextId)
		fmt.Printf("  💧 LP #%d created at %s, all ticker balances initialized to %d\n",
			simParams.NumLiquidityProviders-i+1, nextId.String(), initLpTokenBalance)
	}

	fmt.Println("\nNow creating traders...")
	swapInitcodeId := UploadInitcode(client, "./assets/bytecode/TypicalV3Swap.bin")
	fmt.Printf("Trader initcode is at file %s\n", swapInitcodeId.String())
	var traderIds []string
	for i := simParams.NumTraders; i > 0; i-- {
		nextId := createTrader(routerId, swapInitcodeId, client)
		traderIds = append(traderIds, nextId.ToSolidityAddress())
		fundAccountVia(client, initTraderTokenBalance, typedTokenIds, nextId)
		fmt.Printf("  🕯 Trader #%d created at %s, all ticker balances initialized to %d\n",
			simParams.NumTraders-i+1, nextId.String(), initTraderTokenBalance)
	}

	simDetails := details{
		LpIds:     lpIds,
		Tickers:   tickers,
		TokenIds:  tokenIds,
		TraderIds: traderIds,
		FactoryId: factoryId.String(),
		Weth9Id:   weth9Id.String(),
	}

	fmt.Println("\nNow doing pre-mints...")
	for i := simParams.NumStartupMints; i > 0; i-- {
		MintRandomlyGiven(client, simDetails, simParams)
	}

	rawSimDetails, err := json.Marshal(simDetails)
	if err != nil {
		panic(err)
	}
	err = ioutil.WriteFile("./assets/details.json", rawSimDetails, 0644)
	if err != nil {
		panic(err)
	}
}

func createLp(
	weth9Id hedera.ContractID,
	factoryId hedera.ContractID,
	posManagerId hedera.ContractID,
	lpInitcodeId hedera.FileID,
	client *hedera.Client,
) hedera.ContractID {
	var liqConsParams = hedera.NewContractFunctionParameters()
	_, err := liqConsParams.AddAddress(posManagerId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = liqConsParams.AddAddress(factoryId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = liqConsParams.AddAddress(weth9Id.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	liqManagerId := createContractVia(client, lpInitcodeId, liqConsParams)
	return liqManagerId
}

func createTrader(
	routerId hedera.ContractID,
	swapInitcodeId hedera.FileID,
	client *hedera.Client,
) hedera.ContractID {
	var swapConsParams = hedera.NewContractFunctionParameters()
	_, err := swapConsParams.AddAddress(routerId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	traderId := createContractVia(client, swapInitcodeId, swapConsParams)
	return traderId
}

func createPositionManager(
	weth9Id hedera.ContractID,
	factoryId hedera.ContractID,
	posDescId hedera.ContractID,
	client *hedera.Client,
) hedera.ContractID {
	posManagerInitcodeId := UploadInitcode(client, "./assets/bytecode/NonfungiblePositionManager.bin")
	fmt.Printf("Position manager initcode is at file %s\n", posManagerInitcodeId.String())
	var managerConsParams = hedera.NewContractFunctionParameters()
	_, err := managerConsParams.AddAddress(factoryId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = managerConsParams.AddAddress(weth9Id.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = managerConsParams.AddAddress(posDescId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	posManagerId := createContractVia(client, posManagerInitcodeId, managerConsParams)
	fmt.Printf("🔮️ Token position manager contract deployed to %s\n\n", posManagerId.String())
	return posManagerId
}

func createNftPositionDescriptor(
	weth9Id hedera.ContractID,
	client *hedera.Client,
) hedera.ContractID {
	posDescInitcodeId := UploadInitcode(client, "./assets/bytecode/NonfungibleTokenPositionDescriptor.bin")
	fmt.Printf("Token position descriptor initcode is at file %s\n", posDescInitcodeId.String())
	var descConsParams = hedera.NewContractFunctionParameters()
	_, err := descConsParams.AddAddress(weth9Id.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	var nativeTokenDesc [32]byte
	copy(nativeTokenDesc[30:], "ℏ")
	descConsParams.AddBytes32(nativeTokenDesc)
	tokenPosDescId := createContractVia(client, posDescInitcodeId, descConsParams)
	fmt.Printf("📝 Token position descriptor contract deployed to %s\n\n", tokenPosDescId.String())
	return tokenPosDescId
}

func createRouter(
	weth9Id hedera.ContractID,
	factoryId hedera.ContractID,
	client *hedera.Client,
) hedera.ContractID {
	routerInitcodeId := UploadInitcode(client, "./assets/bytecode/SwapRouter.bin")
	fmt.Printf("Router initcode is at file %s\n", routerInitcodeId.String())
	var routerConsParams = hedera.NewContractFunctionParameters()
	_, err := routerConsParams.AddAddress(factoryId.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	_, err = routerConsParams.AddAddress(weth9Id.ToSolidityAddress())
	if err != nil {
		panic(err)
	}
	routerId := createContractVia(client, routerInitcodeId, routerConsParams)
	fmt.Printf("🔀 Router contract deployed to %s\n\n", routerId.String())
	return routerId
}

func createFactory(
	client *hedera.Client,
) hedera.ContractID {
	factoryInitcodeId := UploadInitcode(client, "./assets/bytecode/UniswapV3Factory.bin")
	fmt.Printf("Factory initcode is at file %s\n", factoryInitcodeId.String())
	factoryId := createContractVia(client, factoryInitcodeId, &hedera.ContractFunctionParameters{})
	fmt.Printf("🏭 Factory contract deployed to %s\n\n", factoryId.String())
	return factoryId
}

func createWETH9(
	client *hedera.Client,
) hedera.ContractID {
	weth9InitcodeId := UploadInitcode(client, "./assets/bytecode/WETH9.bin")
	fmt.Printf("WETH9 initcode is at file %s\n", weth9InitcodeId.String())
	weth9Id := createContractVia(client, weth9InitcodeId, &hedera.ContractFunctionParameters{})
	fmt.Printf("Ⓔ  WETH9 contract deployed to %s\n\n", weth9Id.String())
	return weth9Id
}


func fundAccountVia(
	client *hedera.Client,
	initBalance uint64,
	tokenIds []hedera.ContractID,
	recipient hedera.ContractID,
) {
	for _, tokenId := range tokenIds {
		var transferParams, err = hedera.NewContractFunctionParameters().
			AddAddress(recipient.ToSolidityAddress())
		if err != nil {
			panic(err)
		}
		transferParams.AddUint256(Uint256From64(initBalance))
		CallContractVia(client, tokenId, "transfer", transferParams)
	}
}

func Uint256From64(v uint64) []byte {
	ans := make([]byte, 32)
	binary.BigEndian.PutUint64(ans[24:32], v)
	return ans
}

func Uint64From256(v []byte) uint64 {
	return binary.BigEndian.Uint64(v[24:32])
}

func createContractVia(
	client *hedera.Client,
	initcode hedera.FileID,
	params *hedera.ContractFunctionParameters,
) hedera.ContractID {
	txnId, err := hedera.NewContractCreateTransaction().
		SetGas(4_000_000).
		SetConstructorParameters(params).
		SetBytecodeFileID(initcode).
		Execute(client)
	if err != nil {
		panic(err)
	}

	record, err := txnId.GetRecord(client)
	if err != nil {
		panic(err)
	}

	return *record.Receipt.ContractID
}

func symbolFor(erc20Token string) string {
	words := strings.Split(erc20Token, " ")
	var symbol = "$"
	for _, word := range words {
		symbol += word[0:1]
	}
	return symbol
}
