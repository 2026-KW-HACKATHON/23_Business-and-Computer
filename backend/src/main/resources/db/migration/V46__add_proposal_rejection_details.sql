-- 제안을 누가 언제 거절했는지 기록한다. 사장님은 결제 전 받은 제안을, 학생은 결제된 의뢰서를 거절한다.
-- 배포 이후의 거절부터 기록하고 기존 REJECTED 행은 두 컬럼을 null로 둔다.

ALTER TABLE proposals
    ADD COLUMN rejected_by VARCHAR(20),
    ADD COLUMN rejected_at TIMESTAMP;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_rejected_by_check CHECK (rejected_by IN ('OWNER', 'STUDENT'));
