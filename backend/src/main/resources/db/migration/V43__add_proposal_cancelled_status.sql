-- 제안한 학생이 결제 전 제안을 취소할 수 있도록 상태값에 CANCELLED를 추가한다.
-- 기존 행과 상태값은 그대로 두고 허용 값만 넓힌다.

ALTER TABLE proposals
    DROP CONSTRAINT proposals_status_check;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_status_check
        CHECK (status IN ('PENDING', 'AWAITING_START', 'ACCEPTED', 'REJECTED', 'CANCELLED'));
