-- V30: Student Journal > Add a trade > "Type of Trading"
-- (Equities, Mutual Funds, ETFs, Options, Futures, Commodities). Nullable so existing entries stay valid.

ALTER TABLE trade_journal ADD COLUMN trading_type VARCHAR(20);
