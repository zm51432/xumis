-- ============================================================================
--  shine星心 独立站 · MySQL 建库脚本
--  ---------------------------------------------------------------------------
--  用法（任选一种）：
--    A. 应用会自动建库（连接串带 createDatabaseIfNotExist=true），可跳过本脚本
--    B. 手工建库：
--         mysql -u root -p < sql/init.sql
--       或
--         mysql -u root -p -e "CREATE DATABASE IF NOT EXISTS shine
--           DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
--
--  说明：数据表由 Hibernate（ddl-auto: update）在首次启动时自动创建，
--        本脚本只负责建库与字符集，保证中文与 emoji 正常存储。
-- ============================================================================

CREATE DATABASE IF NOT EXISTS shine
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

-- 如需独立账号（推荐）：
-- CREATE USER IF NOT EXISTS 'shine'@'%' IDENTIFIED BY '换成强密码';
-- GRANT ALL PRIVILEGES ON shine.* TO 'shine'@'%';
-- FLUSH PRIVILEGES;

USE shine;

-- 常用运维查询（按需取消注释执行）：
-- SHOW TABLES;                       -- 查看自动建出的表
-- SELECT id, name, price, stock FROM product;   -- 查看商品
