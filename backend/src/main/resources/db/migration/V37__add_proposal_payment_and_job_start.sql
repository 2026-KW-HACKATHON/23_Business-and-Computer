-- 제안(Proposal)을 사장님이 결제하면 수락 대기(AWAITING_START) 의뢰를 만들고, 학생이 작업을 시작하면 진행 중으로 넘긴다.
-- 기존 행과 상태값은 그대로 두며 과거 제안에 의뢰를 만들지 않는다. 물리 FK는 추가하지 않는다.

ALTER TABLE proposals
    DROP CONSTRAINT proposals_status_check;

ALTER TABLE proposals
    ADD CONSTRAINT proposals_status_check
        CHECK (status IN ('PENDING', 'AWAITING_START', 'ACCEPTED', 'REJECTED'));

-- acceptance_message는 결제 시 사장님이 남긴 한마디다. 취소 시 남기는 message_to_student와 분리한다.
ALTER TABLE jobs
    ADD COLUMN proposal_id BIGINT,
    ADD COLUMN started_at TIMESTAMP,
    ADD COLUMN acceptance_message TEXT;

-- 제안당 의뢰 하나. 제안 없이 만든 일반 의뢰(NULL)는 제약 대상이 아니다.
ALTER TABLE jobs
    ADD CONSTRAINT jobs_proposal_id_key UNIQUE (proposal_id);

-- 제안 결제는 준비 중에 의뢰·지원서가 없으므로 참조를 비울 수 있게 한다.
ALTER TABLE payments
    ALTER COLUMN job_id DROP NOT NULL,
    ALTER COLUMN job_application_id DROP NOT NULL,
    ADD COLUMN proposal_id BIGINT,
    ADD COLUMN revision_count INTEGER,
    ADD COLUMN message_to_student TEXT;

-- 일반 결제는 의뢰·지원서 참조가 필수다. 제안 결제는 지원서를 쓰지 않고 수정 횟수를 주문별로 보존한다.
ALTER TABLE payments
    ADD CONSTRAINT payments_reference_check CHECK (
        (proposal_id IS NULL
            AND job_id IS NOT NULL
            AND job_application_id IS NOT NULL
            AND revision_count IS NULL
            AND message_to_student IS NULL)
        OR (proposal_id IS NOT NULL
            AND job_application_id IS NULL
            AND revision_count IS NOT NULL
            AND revision_count >= 0)
    );

-- 승인된 제안 결제는 승인과 함께 만든 의뢰에 연결되어 있어야 한다.
ALTER TABLE payments
    ADD CONSTRAINT payments_proposal_job_check CHECK (
        proposal_id IS NULL
        OR status NOT IN ('PAID', 'REFUNDED')
        OR job_id IS NOT NULL
    );

CREATE UNIQUE INDEX payments_one_pending_per_proposal_key
    ON payments (proposal_id) WHERE status = 'PENDING';

CREATE UNIQUE INDEX payments_one_paid_per_proposal_key
    ON payments (proposal_id) WHERE status = 'PAID';
