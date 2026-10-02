ALTER TABLE student_profiles
    ALTER COLUMN portfolio_url DROP NOT NULL;

ALTER TABLE student_profiles
    ADD CONSTRAINT student_profiles_student_number_key UNIQUE (student_number);
