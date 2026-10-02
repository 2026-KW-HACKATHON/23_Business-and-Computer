-- 탐색 목록(GET /explore)의 커서 조회가 정렬 순서 그대로 인덱스를 따라 읽도록 한다.
-- 최신순은 역방향, 오래된순은 정방향으로 같은 인덱스를 쓴다.
CREATE INDEX proposals_created_at_id_idx ON proposals (created_at, id);
CREATE INDEX proposals_like_count_created_at_id_idx ON proposals (like_count, created_at, id);
CREATE INDEX jobs_created_at_id_idx ON jobs (created_at, id);
