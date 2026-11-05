
CREATE TABLE IF NOT EXISTS shine.order_return (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  return_no VARCHAR(64) NOT NULL,
  order_no VARCHAR(64) NOT NULL,
  product_id BIGINT NOT NULL,
  quantity INT NOT NULL,
  refund_amount DECIMAL(10,2),
  reason VARCHAR(500),
  status VARCHAR(24) NOT NULL DEFAULT 'APPLIED',
  source VARCHAR(32),
  created_at DATETIME,
  UNIQUE KEY uq_return_product (return_no, product_id),
  INDEX idx_order (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO shine_erp.erp_product(product_id,name,name_en,category,price,material,stock,customizable,image_url)
SELECT id,name,name_en,category,price,material,IFNULL(stock,0),IF(customizable,1,0),image_url FROM shine.product
ON DUPLICATE KEY UPDATE name=VALUES(name),name_en=VALUES(name_en),category=VALUES(category),
 price=VALUES(price),material=VALUES(material),stock=VALUES(stock),customizable=VALUES(customizable),image_url=VALUES(image_url);

INSERT INTO shine_erp.erp_sales_order(order_no,customer_name,email,phone,country,address,status,currency,payment_method,total_amount,placed_at)
SELECT order_no,customer_name,email,phone,country,address,status,IFNULL(currency,'USD'),payment_method,total_amount,created_at FROM shine.shop_order
ON DUPLICATE KEY UPDATE status=VALUES(status),total_amount=VALUES(total_amount),customer_name=VALUES(customer_name),
 email=VALUES(email),phone=VALUES(phone),country=VALUES(country),address=VALUES(address),payment_method=VALUES(payment_method);

INSERT INTO shine_erp.erp_sales_item(order_no,product_id,product_name,price,quantity,line_total)
SELECT o.order_no, oi.product_id, oi.product_name, oi.price, IFNULL(oi.quantity,1), oi.price*IFNULL(oi.quantity,1)
FROM shine.order_item oi JOIN shine.shop_order o ON o.id=oi.order_id
ON DUPLICATE KEY UPDATE product_name=VALUES(product_name),price=VALUES(price),quantity=VALUES(quantity),line_total=VALUES(line_total);
