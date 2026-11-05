
CREATE DATABASE IF NOT EXISTS shine_erp CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'erp'@'localhost' IDENTIFIED WITH mysql_native_password BY '<ERP_DB_PASSWORD>';
ALTER USER 'erp'@'localhost' IDENTIFIED WITH mysql_native_password BY '<ERP_DB_PASSWORD>';
GRANT ALL PRIVILEGES ON shine_erp.* TO 'erp'@'localhost';
GRANT SELECT ON shine.* TO 'erp'@'localhost';
FLUSH PRIVILEGES;

USE shine_erp;

CREATE TABLE IF NOT EXISTS erp_user (
  id INT AUTO_INCREMENT PRIMARY KEY,
  username VARCHAR(64) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_product (
  product_id BIGINT PRIMARY KEY,
  name VARCHAR(255), name_en VARCHAR(255), category VARCHAR(64),
  price DECIMAL(10,2), material VARCHAR(255), stock INT DEFAULT 0,
  customizable TINYINT(1) DEFAULT 0, image_url VARCHAR(500),
  active TINYINT(1) NOT NULL DEFAULT 1,   -- 0 = 已从 ERP 台账移除（软删除，网站同步不会恢复）
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_inventory_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  product_id BIGINT NOT NULL,
  change_qty INT NOT NULL,
  before_qty INT NOT NULL,
  after_qty INT NOT NULL,
  type VARCHAR(24) NOT NULL,
  ref_no VARCHAR(128),
  note VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_product (product_id), INDEX idx_ref (ref_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_sales_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL UNIQUE,
  customer_name VARCHAR(128), email VARCHAR(180), phone VARCHAR(64),
  country VARCHAR(80), address VARCHAR(500),
  status VARCHAR(32), currency VARCHAR(8) DEFAULT 'USD',
  payment_method VARCHAR(32),
  total_amount DECIMAL(10,2) DEFAULT 0,
  placed_at DATETIME,
  synced_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_sales_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  order_no VARCHAR(64) NOT NULL,
  product_id BIGINT, product_name VARCHAR(255),
  price DECIMAL(10,2), quantity INT DEFAULT 1,
  line_total DECIMAL(10,2),
  UNIQUE KEY uq_order_product (order_no, product_id),
  INDEX idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_return (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  return_no VARCHAR(64) NOT NULL UNIQUE,
  order_no VARCHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL DEFAULT 'DRAFT',
  reason VARCHAR(500),
  refund_amount DECIMAL(10,2) DEFAULT 0,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  approved_at DATETIME NULL,
  synced_at DATETIME NULL,
  website_response TEXT,
  INDEX idx_order (order_no), INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_return_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  return_id BIGINT NOT NULL,
  product_id BIGINT NOT NULL,
  product_name VARCHAR(255),
  price DECIMAL(10,2),
  quantity INT NOT NULL,
  refund_amount DECIMAL(10,2),
  UNIQUE KEY uq_return_product (return_id, product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_inquiry (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_id BIGINT, name VARCHAR(128), email VARCHAR(180),
  subject VARCHAR(255), message TEXT, created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_custom_idea (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  source_id BIGINT, customer_name VARCHAR(128), email VARCHAR(180),
  country VARCHAR(80), idea_description TEXT, expect_category VARCHAR(64),
  reference_image_url VARCHAR(500), created_at DATETIME
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS erp_sync_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  direction VARCHAR(8) NOT NULL,
  endpoint VARCHAR(120),
  status VARCHAR(16),
  message VARCHAR(500),
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
