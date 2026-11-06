CREATE TABLE user_article_favorite (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_id BIGINT NOT NULL,article_id BIGINT NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
 CONSTRAINT article_favorite_user FOREIGN KEY(user_id) REFERENCES identity_user(id),
 CONSTRAINT article_favorite_article FOREIGN KEY(article_id) REFERENCES reading_article(id),
 UNIQUE KEY article_favorite_owner(user_id,article_id),
 INDEX article_favorite_listing(user_id,active,updated_at,id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
