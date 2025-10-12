-- RAG资料售卖平台数据库表结构

-- 1. 资料分类表
CREATE TABLE IF NOT EXISTS `t_material_category` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `parent_id` bigint(20) DEFAULT 0 COMMENT '父分类ID，0表示顶级分类',
  `sort_order` int(11) DEFAULT 0 COMMENT '排序号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料分类表';

-- 2. 资料表
CREATE TABLE IF NOT EXISTS `t_material` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `title` varchar(200) NOT NULL COMMENT '资料标题',
  `description` text COMMENT '资料描述',
  `category_id` bigint(20) NOT NULL COMMENT '分类ID',
  `seller_id` bigint(20) NOT NULL COMMENT '卖家用户ID',
  `price` int(11) NOT NULL DEFAULT 0 COMMENT '价格（积分）',
  `file_url` varchar(500) NOT NULL COMMENT '文件URL',
  `file_type` varchar(50) DEFAULT NULL COMMENT '文件类型（pdf/doc/txt等）',
  `file_size` bigint(20) DEFAULT NULL COMMENT '文件大小（字节）',
  `cover_url` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：0-下架，1-上架',
  `view_count` int(11) DEFAULT 0 COMMENT '浏览次数',
  `sales_count` int(11) DEFAULT 0 COMMENT '销售数量',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料表';

-- 3. 资料订单表
CREATE TABLE IF NOT EXISTS `t_material_order` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `order_no` varchar(64) NOT NULL COMMENT '订单号',
  `material_id` bigint(20) NOT NULL COMMENT '资料ID',
  `buyer_id` bigint(20) NOT NULL COMMENT '买家用户ID',
  `seller_id` bigint(20) NOT NULL COMMENT '卖家用户ID',
  `price` int(11) NOT NULL COMMENT '交易价格（积分）',
  `pay_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '支付方式：1-积分，2-支付宝，3-微信',
  `status` tinyint(4) NOT NULL DEFAULT 0 COMMENT '订单状态：0-待支付，1-已支付，2-已取消，3-已退款',
  `pay_time` datetime DEFAULT NULL COMMENT '支付时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_buyer_id` (`buyer_id`),
  KEY `idx_seller_id` (`seller_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料订单表';

-- 4. 资料访问权限表
CREATE TABLE IF NOT EXISTS `t_material_access` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint(20) NOT NULL COMMENT '用户ID',
  `material_id` bigint(20) NOT NULL COMMENT '资料ID',
  `access_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT '访问类型：1-购买，2-赠送',
  `order_id` bigint(20) DEFAULT NULL COMMENT '关联订单ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_material` (`user_id`, `material_id`),
  KEY `idx_material_id` (`material_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='资料访问权限表';

-- 插入默认分类数据
INSERT INTO `t_material_category` (`id`, `name`, `parent_id`, `sort_order`) VALUES
(1, '计算机科学', 0, 1),
(2, '人工智能', 1, 1),
(3, '机器学习', 1, 2),
(4, '深度学习', 1, 3),
(5, '自然语言处理', 1, 4),
(6, '数学', 0, 2),
(7, '物理', 0, 3),
(8, '化学', 0, 4),
(9, '生物', 0, 5),
(10, '其他', 0, 99);
