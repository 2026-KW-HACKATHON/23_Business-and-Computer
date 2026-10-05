-- 데모 로그인(/demo/login)이 만든 데이터의 격리 범위. NULL이면 실제 데이터다.
-- 조회자와 값이 같은 데이터만 탐색 목록에 보이고, 값이 같은 사용자끼리만 지원·제안할 수 있다.

ALTER TABLE users ADD COLUMN demo_session_id VARCHAR(26);
ALTER TABLE owner_profiles ADD COLUMN demo_session_id VARCHAR(26);
ALTER TABLE jobs ADD COLUMN demo_session_id VARCHAR(26);
ALTER TABLE proposals ADD COLUMN demo_session_id VARCHAR(26);

CREATE INDEX users_demo_session_id_idx ON users (demo_session_id) WHERE demo_session_id IS NOT NULL;
CREATE INDEX owner_profiles_demo_session_id_idx ON owner_profiles (demo_session_id) WHERE demo_session_id IS NOT NULL;
CREATE INDEX jobs_demo_session_id_idx ON jobs (demo_session_id) WHERE demo_session_id IS NOT NULL;
CREATE INDEX proposals_demo_session_id_idx ON proposals (demo_session_id) WHERE demo_session_id IS NOT NULL;
