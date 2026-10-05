-- 개발용 테스트 로그인(POST /dev/login) 전용 계정.
-- username이 소셜 로그인 형식(KAKAO_<id>)이 아니라 소셜 로그인으로는 들어올 수 없고,
-- dev-login.enabled가 꺼진 서버에서는 토큰을 발급할 경로가 없어 쓰이지 않는다.
-- 사업자번호·이메일은 가입 요청 검증을 통과할 수 없는 값이라 실제 가입과 겹치지 않고, 학번은 입학 연도를 2099로 둔다.

INSERT INTO users (user_id, email, name, username, is_lock, role, created_at, updated_at)
VALUES ('01K6DEV0000000000000000001', NULL, '테스트 사장님', 'DEV_OWNER', FALSE, 'OWNER',
        LOCALTIMESTAMP, LOCALTIMESTAMP),
       ('01K6DEV0000000000000000002', 'dev-student@example.com', '테스트 학생', 'DEV_STUDENT', FALSE, 'STUDENT',
        LOCALTIMESTAMP, LOCALTIMESTAMP);

-- 업종 기준 데이터는 마이그레이션으로 관리하지 않는다. 업종이 하나도 없는 DB에서만 대체 업종을 넣는다.
INSERT INTO business_categories (name, created_at, updated_at)
SELECT '기타', LOCALTIMESTAMP, LOCALTIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM business_categories);

INSERT INTO owner_profiles (user_id, business_number, store_name, category_id, created_at, updated_at)
SELECT '01K6DEV0000000000000000001', 'DEV-LOGIN-OWNER', '가꿈 테스트 매장', MIN(id),
       LOCALTIMESTAMP, LOCALTIMESTAMP
FROM business_categories;

INSERT INTO student_profiles (user_id, university, student_number, major, created_at, updated_at)
VALUES ('01K6DEV0000000000000000002', '광운대학교', '2099000001', '소프트웨어학부',
        LOCALTIMESTAMP, LOCALTIMESTAMP);
