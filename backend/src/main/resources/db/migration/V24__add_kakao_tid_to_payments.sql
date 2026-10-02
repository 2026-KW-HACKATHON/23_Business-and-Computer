ALTER TABLE payments ADD COLUMN kakao_tid VARCHAR(20);

ALTER TABLE payments ADD CONSTRAINT payments_kakao_tid_key UNIQUE (kakao_tid);
