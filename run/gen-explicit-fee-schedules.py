### 
# A script to convert the Services-consumable feeSchedules.json 
# into the "typed" format used by the public pricing calculator.
### 

import json

providers = ['nodedata', 'networkdata', 'servicedata']
typed_schedules = {}
with open('hedera-node/src/main/resources/feeSchedules.json', 'r') as fin:
    cur_and_next_schedules = json.load(fin)
    schedules = cur_and_next_schedules[0]['currentFeeSchedule']
    for tfs in schedules:
        if 'expiryTime' in tfs:
            break
        tfs = tfs['transactionFeeSchedule']
        function = tfs['hederaFunctionality']
        prices_list = tfs['fees']
        prices_by_type = {}
        for typed_prices in prices_list:
            this_type = typed_prices.get('subType', 'DEFAULT')
            this_type_prices = {}
            for provider in providers:
                this_type_prices[provider] = typed_prices[provider]
            prices_by_type[this_type] = this_type_prices
        typed_schedules[function] = prices_by_type

with open('typedFeeSchedules.json', 'w') as fout:
    json.dump(typed_schedules, fout, indent=2)
