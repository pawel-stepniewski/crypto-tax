# --- Create transactions table

# --- !Ups

create table TRANSACTIONS (
  ID                 BIGSERIAL      NOT NULL,
  CURRENCY_FROM      VARCHAR(3)     NOT NULL,
  CURRENCY_TO        VARCHAR(3)     NOT NULL,
  FROM_VALUE         BIGINT         NOT NULL,
  TO_VALUE           BIGINT         NOT NULL,
  EXCHANGE_RATE      BIGINT         NOT NULL,
  TX_ROLE            VARCHAR(8)     NOT NULL,
  COMMISSION         VARCHAR(3)     NULL,
  TX_DATETIME        TIMESTAMP      NOT NULL,

  constraint PK_TRANSACTIONS_ID primary key (ID)
);
