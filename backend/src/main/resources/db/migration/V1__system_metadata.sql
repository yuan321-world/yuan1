CREATE TABLE system_metadata (
    metadata_key VARCHAR(64) NOT NULL,
    metadata_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (metadata_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO system_metadata (metadata_key, metadata_value)
VALUES ('service_name', 'english-hero');
