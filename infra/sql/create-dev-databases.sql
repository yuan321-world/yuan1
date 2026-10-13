-- 使用管理员连接本地 MySQL，先仅运行此查询。
SELECT SCHEMA_NAME FROM INFORMATION_SCHEMA.SCHEMATA
WHERE SCHEMA_NAME IN ('english_hero_dev', 'english_hero_test');

-- 只有上述结果为空才继续；已有任何同名库时先核实用途，禁止清库或覆盖。
-- 不使用 IF NOT EXISTS，避免把已有库误当成新建项目库。
CREATE DATABASE english_hero_dev CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE english_hero_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 在数据库工具的账号管理界面分别创建：
-- english_hero_dev@127.0.0.1 和 english_hero_test@127.0.0.1。
-- 密码通过工具输入，不保存在此脚本、命令行参数或聊天中。
-- 先核实账号没有其他权限；下列 GRANT 不会撤销既有权限。
-- 仅授予迁移需要的 DDL 与数据访问权限，不授予 DROP、GRANT OPTION 或全局权限。
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON english_hero_dev.* TO 'english_hero_dev'@'127.0.0.1';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
ON english_hero_test.* TO 'english_hero_test'@'127.0.0.1';

-- 分别以项目账号重新连接对应库后执行：
SELECT VERSION(), DATABASE(), @@session.time_zone;
SHOW GRANTS FOR CURRENT_USER;
