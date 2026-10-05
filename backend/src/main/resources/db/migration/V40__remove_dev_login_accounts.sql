-- 개발용 테스트 로그인(/dev/login)을 데모 로그인(/demo/login)으로 대체하면서 V39가 넣은 공유 테스트 계정을 지운다.
-- V39가 업종이 없는 DB에 넣은 대체 업종은 다른 매장이 쓰고 있을 수 있어 남긴다.

DELETE FROM owner_profiles WHERE user_id = '01K6DEV0000000000000000001';
DELETE FROM student_profiles WHERE user_id = '01K6DEV0000000000000000002';
DELETE FROM refresh_tokens WHERE username IN ('DEV_OWNER', 'DEV_STUDENT');
DELETE FROM users WHERE user_id IN ('01K6DEV0000000000000000001', '01K6DEV0000000000000000002');
