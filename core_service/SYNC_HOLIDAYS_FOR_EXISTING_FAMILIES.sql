-- =====================================================
-- ĐỒNG BỘ NGÀY LỄ CHO TẤT CẢ FAMILY ĐÃ TỒN TẠI
-- Chạy script này 1 LẦN DUY NHẤT trong MySQL Workbench/phpMyAdmin
-- =====================================================

-- 1. Lễ Mừng Thọ - Tết Nguyên Đán (17/02/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Lễ Mừng Thọ - Tết Nguyên Đán', 
'Ngày lễ truyền thống để chúc phúc cho người cao tuổi trong gia đình. Đây là dịp để con cháu thể hiện lòng hiếu thảo và mong ông bà sống lâu trăm tuổi.',
'2026-02-17 08:00:00', '2026-02-17 20:00:00', 'Nhà riêng', NOW(), NOW()
FROM families f;

-- 2. Ngày Quốc tế Phụ nữ 8/3 (08/03/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày Quốc tế Phụ nữ 8/3',
'Ngày tôn vinh phụ nữ trên toàn thế giới. Đây là dịp để tặng quà và bày tỏ lòng biết ơn đến mẹ, bà, vợ và các thành viên nữ trong gia đình.',
'2026-03-08 00:00:00', '2026-03-08 23:59:59', '', NOW(), NOW()
FROM families f;

-- 3. Ngày của Mẹ - Mother's Day (10/05/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày của Mẹ - Mother''s Day',
'Ngày quốc tế để tôn vinh và tri ân công lao sinh thành, dưỡng dục của người mẹ. Hãy dành thời gian bên mẹ và bày tỏ tình yêu thương.',
'2026-05-10 00:00:00', '2026-05-10 23:59:59', '', NOW(), NOW()
FROM families f;

-- 4. Ngày Quốc tế Thiếu nhi 1/6 (01/06/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày Quốc tế Thiếu nhi 1/6',
'Ngày dành cho trẻ em trên toàn thế giới. Đây là dịp để tổ chức các hoạt động vui chơi và tặng quà cho con cái.',
'2026-06-01 00:00:00', '2026-06-01 23:59:59', '', NOW(), NOW()
FROM families f;

-- 5. Ngày của Cha - Father's Day (21/06/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày của Cha - Father''s Day',
'Ngày quốc tế để tôn vinh và tri ân công lao của người cha. Hãy dành thời gian bên cha và bày tỏ lòng biết ơn.',
'2026-06-21 00:00:00', '2026-06-21 23:59:59', '', NOW(), NOW()
FROM families f;

-- 6. Ngày Gia đình Việt Nam 28/6 (28/06/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày Gia đình Việt Nam 28/6',
'Ngày lễ để tất cả các thành viên trong gia đình sum họp, chia sẻ và gắn kết tình cảm. Đây là dịp để gia đình quây quần bên nhau.',
'2026-06-28 00:00:00', '2026-06-28 23:59:59', 'Nhà riêng', NOW(), NOW()
FROM families f;

-- 7. Lễ Vu Lan - Ngày Báo hiếu (28/08/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Lễ Vu Lan - Ngày Báo hiếu',
'Ngày lễ quan trọng nhất trong văn hóa Việt Nam để con cái bày tỏ lòng biết ơn sâu sắc đến cha mẹ và tổ tiên. Nhằm ngày 15/7 Âm lịch.',
'2026-08-28 00:00:00', '2026-08-28 23:59:59', 'Chùa hoặc nhà riêng', NOW(), NOW()
FROM families f;

-- 8. Tết Trung Thu - Tết Đoàn viên (25/09/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Tết Trung Thu - Tết Đoàn viên',
'Tết của trẻ em và là dịp để gia đình sum họp, đoàn viên. Nhằm ngày 15/8 Âm lịch. Hãy cùng nhau thưởng trăng, ăn bánh trung thu và vui chơi.',
'2026-09-25 18:00:00', '2026-09-25 22:00:00', 'Nhà riêng', NOW(), NOW()
FROM families f;

-- 9. Ngày Quốc tế Người cao tuổi 1/10 (01/10/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày Quốc tế Người cao tuổi 1/10',
'Ngày để tri ân công lao của ông bà, người cao tuổi trong gia đình. Hãy dành thời gian thăm hỏi và chăm sóc ông bà.',
'2026-10-01 00:00:00', '2026-10-01 23:59:59', '', NOW(), NOW()
FROM families f;

-- 10. Ngày Phụ nữ Việt Nam 20/10 (20/10/2026)
INSERT INTO family_events (family_id, creator_id, title, description, start_time, end_time, location, created_at, updated_at)
SELECT f.id, f.user_id, 'Ngày Phụ nữ Việt Nam 20/10',
'Ngày tôn vinh phụ nữ Việt Nam. Đây là dịp để tặng quà và bày tỏ lòng biết ơn đến mẹ, bà, vợ và các thành viên nữ trong gia đình.',
'2026-10-20 00:00:00', '2026-10-20 23:59:59', '', NOW(), NOW()
FROM families f;

-- =====================================================
-- KIỂM TRA KẾT QUẢ
-- =====================================================

-- Xem tổng số events đã thêm
SELECT COUNT(*) as total_events FROM family_events;

-- Xem số events theo từng family
SELECT f.name, COUNT(fe.event_id) as event_count
FROM families f
LEFT JOIN family_events fe ON f.id = fe.family_id
GROUP BY f.id, f.name;

-- Xem danh sách events của 1 family cụ thể (thay YOUR_FAMILY_ID)
-- SELECT title, start_time FROM family_events WHERE family_id = 'YOUR_FAMILY_ID' ORDER BY start_time;
