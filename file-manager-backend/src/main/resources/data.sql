-- 初始化默认管理员账号
-- 用户名: admin
-- 密码: admin123
-- 请在生产环境中修改默认密码！

INSERT INTO user (username, password, email, nickname, role, status, created_at, updated_at)
SELECT 'admin', '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iKTVKIUi', 'admin@example.com', '系统管理员', 'ADMIN', 'ACTIVE', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM user WHERE username = 'admin');