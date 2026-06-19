-- ============================================================
-- Seed the 40 fixed time slots (Mon-Fri x 8 modules) and the
-- singleton constraint-weights row with sensible defaults.
-- TimeSlot id = dayIndex(1..5)*10 + slotIndex(1..8).
-- ============================================================

INSERT INTO time_slot (id, day_of_week, slot_index, start_time, end_time) VALUES
 (11,'MONDAY',1,'08:00','09:30'),(12,'MONDAY',2,'09:40','11:10'),
 (13,'MONDAY',3,'11:20','12:50'),(14,'MONDAY',4,'13:00','14:30'),
 (15,'MONDAY',5,'14:40','16:10'),(16,'MONDAY',6,'16:20','17:50'),
 (17,'MONDAY',7,'18:00','19:30'),(18,'MONDAY',8,'19:40','21:10'),
 (21,'TUESDAY',1,'08:00','09:30'),(22,'TUESDAY',2,'09:40','11:10'),
 (23,'TUESDAY',3,'11:20','12:50'),(24,'TUESDAY',4,'13:00','14:30'),
 (25,'TUESDAY',5,'14:40','16:10'),(26,'TUESDAY',6,'16:20','17:50'),
 (27,'TUESDAY',7,'18:00','19:30'),(28,'TUESDAY',8,'19:40','21:10'),
 (31,'WEDNESDAY',1,'08:00','09:30'),(32,'WEDNESDAY',2,'09:40','11:10'),
 (33,'WEDNESDAY',3,'11:20','12:50'),(34,'WEDNESDAY',4,'13:00','14:30'),
 (35,'WEDNESDAY',5,'14:40','16:10'),(36,'WEDNESDAY',6,'16:20','17:50'),
 (37,'WEDNESDAY',7,'18:00','19:30'),(38,'WEDNESDAY',8,'19:40','21:10'),
 (41,'THURSDAY',1,'08:00','09:30'),(42,'THURSDAY',2,'09:40','11:10'),
 (43,'THURSDAY',3,'11:20','12:50'),(44,'THURSDAY',4,'13:00','14:30'),
 (45,'THURSDAY',5,'14:40','16:10'),(46,'THURSDAY',6,'16:20','17:50'),
 (47,'THURSDAY',7,'18:00','19:30'),(48,'THURSDAY',8,'19:40','21:10'),
 (51,'FRIDAY',1,'08:00','09:30'),(52,'FRIDAY',2,'09:40','11:10'),
 (53,'FRIDAY',3,'11:20','12:50'),(54,'FRIDAY',4,'13:00','14:30'),
 (55,'FRIDAY',5,'14:40','16:10'),(56,'FRIDAY',6,'16:20','17:50'),
 (57,'FRIDAY',7,'18:00','19:30'),(58,'FRIDAY',8,'19:40','21:10');

INSERT INTO constraint_weights
 (id, daily_load_balance, group_gap, late_hours_license, compactness, global_weekly_balance, professor_preference)
VALUES (1, 40, 30, 15, 8, 5, 5);
