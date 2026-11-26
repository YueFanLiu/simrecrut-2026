-- Optional country, city or remote policy shown to candidates; not a model input.
ALTER TABLE sim_job_version ADD COLUMN work_location VARCHAR(200) NULL AFTER description;
