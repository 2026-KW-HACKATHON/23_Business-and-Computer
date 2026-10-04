-- 의뢰 관련 데이터 전체 초기화 (수동 실행 전용)
--
-- Flyway가 실행하지 않는다. 실제 거래 데이터가 없는 테스트 DB에서만 사용한다.
-- V36__replace_job_application_content.sql 적용 전에 실행한다.
--
-- 실행 전
--   1. 접속한 DB가 초기화 대상인지 확인한다.
--   2. 애플리케이션을 중지한다.
--   3. DB를 백업한다.
--
-- 실행
--   psql "$DATABASE_URL" -v ON_ERROR_STOP=1 -f db/manual/reset_job_data.sql
--
-- 삭제: jobs, job_specialties, job_applications, payments, job_submissions, reviews,
--       chat_rooms, chat_messages, chat_attachment_uploads
-- 유지: 회원·프로필·특기·제안 데이터, ID 시퀀스, S3 파일, 결제사 거래 내역

BEGIN;

-- 의뢰를 참조하는 이력을 먼저 지우고 의뢰를 마지막에 지운다.
DELETE FROM chat_messages;
DELETE FROM chat_attachment_uploads;
DELETE FROM chat_rooms;
DELETE FROM reviews;
DELETE FROM job_submissions;
DELETE FROM payments;
DELETE FROM job_applications;
DELETE FROM job_specialties;
DELETE FROM jobs;

COMMIT;
