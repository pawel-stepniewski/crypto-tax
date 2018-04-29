# crypto-tax
Simple web app to help calculate cryptocurrency trading income tax (Poland)

*Program is only a help in tax settlement. The author does not take any responsibility for the correctness of the result.*

## Security notes
Authenication mechanism has not been implemented (app should be requested only from trusted machines).

## What it do?
It helps to calculate income tax to PIT36 (private person, not company!) - calculation relying on FIFO method.

Programs print on standard output all 'sell' transactions with it value and cost. To calculate your all incomes and costs just sum all transactions values and costs.

It also print 'buy' transactions which were not included in cost yet (income tax is calculated in FIFO way)

## Import data

Endpoint to import data using csv file:
```curl
curl -v -X POST http://localhost:9000/import \
  -H "Content-Type: text/csv" \
  -H "Expect:" \
  --data-binary @<path_to_csv_file>
```

Sample csv file format (similar to BitBay export file):

**Important**: Remember to fill commissions fields. Without them result can be incorrect.

```
"Market";"Transaction date";"Type";"Kind";"Exchange rate";"Amount";"Value";"Commission PLN";"Commission Crypto"
"BTC - PLN";"31-08-2017 15:28:47";"Kupno";"Taker";"16800.00";"0.36725233";"6169.84";;-0.00143229
```
Type => Acceptable values: "Kupno" ('Buy' transactions), "Sprzedaż" ('Sell' transactions).

Kind => Redundant field, not used.

## Calculate transactions summary

Sample curl to calculate transactions.

**Important**: "from" field should be set to date before first history transaction and "to" field should points last transaction date.


If you started trading in 2017 then to calculate all transaction in 2017 you should call
```
curl -v -X POST http://localhost:9000/income_tax \
    -H "Content-Type: application/json" -d '
    {
        "from": "2017-01-01",
        "to":   "2018-01-01"
    }'
```

## Sample result

Seller transaction => internal_id; crypto_symbol; value_PLN; cost_PLN, transaction_date

Buyer transaction => crypto_symbol; exchange_rate; crypto_amount; value_PLN
```
SELLER TRANSACTIONS:
1641; LSK; 1249.28; 1249.28; 2017-12-22 19:45:58.0
1642; LSK; 908.74; 908.74; 2017-12-22 19:46:01.0
1643; LSK; 500.00; 500.00; 2017-12-22 19:46:02.0
1644; LSK; 926.95; 926.95; 2017-12-22 19:46:06.0

BUYER TRANSACTIONS:
LSK; 77.40; 2.79826119; 229.87
LSK; 77.60; 2.37748236; 230.26
```