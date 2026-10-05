-- 지원서 본문(content)을 한 줄 요약·작업계획서·결과물 전달 방법으로 교체한다.
-- 기존 데이터는 이전하지 않는다. 지원서나 결제 행이 남아 있으면 중단하므로
-- db/manual/reset_job_data.sql로 의뢰 데이터를 먼저 초기화한 뒤 적용한다.
DO $$
DECLARE
    application_count BIGINT;
    payment_count BIGINT;
BEGIN
    SELECT count(*) INTO application_count FROM job_applications;
    SELECT count(*) INTO payment_count FROM payments;

    IF application_count > 0 OR payment_count > 0 THEN
        RAISE EXCEPTION 'V36을 적용할 수 없습니다: job_applications %건, payments %건이 남아 있습니다.',
            application_count, payment_count
            USING HINT = 'db/manual/reset_job_data.sql로 의뢰 데이터를 초기화한 뒤 다시 실행하세요.';
    END IF;
END $$;

ALTER TABLE job_applications
    DROP COLUMN content,
    ADD COLUMN summary VARCHAR(255) NOT NULL,
    ADD COLUMN work_plan VARCHAR(500) NOT NULL,
    ADD COLUMN delivery_method VARCHAR(500) NOT NULL;
