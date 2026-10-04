CREATE INDEX activities_user_date_idx ON activities (user_id, activity_date);
CREATE INDEX training_activities_user_date_idx ON training_activities (user_id, activity_date);
CREATE INDEX training_blocks_user_dates_idx ON training_blocks (user_id, start_date, end_date);

-- Foreign keys.
CREATE INDEX training_activities_block_date_idx ON training_activities (training_block_id, activity_date);
-- A planned activity has at most one linked actual result.
CREATE UNIQUE INDEX activities_training_activity_key
    ON activities (training_activity_id) WHERE training_activity_id IS NOT NULL;
-- Future imports must not create duplicates of the same external activity.
CREATE UNIQUE INDEX activities_source_external_key
    ON activities (user_id, source, external_id) WHERE external_id IS NOT NULL;
