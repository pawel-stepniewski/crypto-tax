# --- Create transactions table

# --- !Ups

create table TRANSACTIONS (
  ID                 BIGSERIAL      NOT NULL,
  CRYPTO_SYMBOL      VARCHAR(3)     NOT NULL,
  CRYPTO_AMOUNT      BIGINT         NOT NULL,
  TX_VALUE           BIGINT         NOT NULL,
  EXCHANGE_RATE      BIGINT         NOT NULL,
  TX_ROLE            VARCHAR(8)     NOT NULL,
  COMMISSION_SELL    BIGINT         NULL,       -- in PLN
  COMMISSION_BUY     BIGINT         NULL,       -- in crypto currency
  TX_DATETIME        TIMESTAMP      NOT NULL,

  constraint PK_TRANSACTIONS_ID primary key (ID)
);
