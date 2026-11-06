CREATE TABLE reading_baidu_monthly_quota (
 month_start DATE NOT NULL PRIMARY KEY,used_characters BIGINT NOT NULL DEFAULT 0,
 CONSTRAINT baidu_quota_nonnegative CHECK(used_characters>=0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
ALTER TABLE reading_selection_translation
 ADD COLUMN source_characters INT NULL,
 ADD COLUMN quota_month DATE NULL,
 ADD CONSTRAINT selection_source_characters CHECK(source_characters IS NULL OR source_characters BETWEEN 1 AND 1000);
