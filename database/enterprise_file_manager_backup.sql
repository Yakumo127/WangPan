-- MySQL dump 10.13  Distrib 8.0.43, for Linux (x86_64)
--
-- Host: 127.0.0.1    Database: enterprise_file_manager
-- ------------------------------------------------------
-- Server version	8.0.43-0ubuntu0.22.04.1

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `file_chunks`
--

DROP TABLE IF EXISTS `file_chunks`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_chunks` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chunk_hash` varchar(64) DEFAULT NULL,
  `chunk_number` int NOT NULL,
  `chunk_path` varchar(500) NOT NULL,
  `chunk_size` bigint NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `upload_status` enum('PENDING','UPLOADING','COMPLETED','FAILED') DEFAULT NULL,
  `file_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKhlh025a1p08hnfjhx16nvuvmn` (`file_id`),
  KEY `FKdkh20ub60lj3u7eihneunqh25` (`user_id`),
  CONSTRAINT `FKdkh20ub60lj3u7eihneunqh25` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKhlh025a1p08hnfjhx16nvuvmn` FOREIGN KEY (`file_id`) REFERENCES `files` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_chunks`
--

LOCK TABLES `file_chunks` WRITE;
/*!40000 ALTER TABLE `file_chunks` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_chunks` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `files`
--

DROP TABLE IF EXISTS `files`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `files` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `content_type` varchar(255) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `delete_time` datetime(6) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `download_count` int DEFAULT NULL,
  `file_hash` varchar(255) NOT NULL,
  `file_path` varchar(255) NOT NULL,
  `filename` varchar(255) NOT NULL,
  `original_filename` varchar(255) NOT NULL,
  `size` bigint NOT NULL,
  `thumbnail_path` varchar(255) DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `folder_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKe9awb46i258gxwjtbjprmtpmi` (`folder_id`),
  KEY `FKdgr5hx49828s5vhjo1s8q3wdp` (`user_id`),
  CONSTRAINT `FKdgr5hx49828s5vhjo1s8q3wdp` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKe9awb46i258gxwjtbjprmtpmi` FOREIGN KEY (`folder_id`) REFERENCES `folders` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `files`
--

LOCK TABLES `files` WRITE;
/*!40000 ALTER TABLE `files` DISABLE KEYS */;
INSERT INTO `files` VALUES (1,'text/plain','2025-09-06 11:35:47.375176',NULL,_binary '\0',NULL,0,'95be0ba40e87565e8850a155495ef3a0cd3dd41ebfaf7304ea15da3b5540ca83','/tmp/cunchu/user_1/fe23f6e8-6017-4bad-b87a-57b10ebac95a.txt','fe23f6e8-6017-4bad-b87a-57b10ebac95a.txt','testfile.txt',19,NULL,'2025-09-06 11:35:47.375247',NULL,1),(2,'text/plain','2025-09-06 12:11:09.662606',NULL,_binary '\0',NULL,0,'d90ef1651fd9e7563e1a1450a16bd784e9a7e2d3df0f5a798dfc1bdf70a64aea','/tmp/cunchu/user_1/656e13b4-0f58-4055-9349-60cf4d71dfd5.txt','656e13b4-0f58-4055-9349-60cf4d71dfd5.txt','test.txt',18,NULL,'2025-09-06 12:11:09.662650',NULL,1),(3,'text/plain','2025-09-06 12:15:04.403724',NULL,_binary '\0',NULL,0,'d90ef1651fd9e7563e1a1450a16bd784e9a7e2d3df0f5a798dfc1bdf70a64aea','/tmp/cunchu/user_1/6dc5a2c7-1a00-4fe1-85ab-e82aea8f5e47.txt','6dc5a2c7-1a00-4fe1-85ab-e82aea8f5e47.txt','test.txt',18,NULL,'2025-09-06 12:15:04.403758',NULL,1),(4,'text/plain','2025-09-06 12:15:20.738487',NULL,_binary '\0',NULL,0,'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','/tmp/cunchu/user_1/b4d32039-7484-4df7-a356-b68f29e2d230.txt','b4d32039-7484-4df7-a356-b68f29e2d230.txt','empty.txt',0,NULL,'2025-09-06 12:15:20.738521',NULL,1),(5,'application/octet-stream','2025-09-06 12:15:51.395268',NULL,_binary '\0',NULL,0,'8565a714dca840f8652c5bae9249ab05f5fb5a4f9f13fbe23304b10f68252da2','/tmp/cunchu/user_1/a9068a19-4a76-4eb5-97f4-f0e490ce19c5.bin','a9068a19-4a76-4eb5-97f4-f0e490ce19c5.bin','largefile.bin',52428800,NULL,'2025-09-06 12:15:51.395309',NULL,1),(6,'text/plain','2025-09-06 12:16:07.596764','2025-09-06 13:23:16.997183',_binary '',NULL,0,'d90ef1651fd9e7563e1a1450a16bd784e9a7e2d3df0f5a798dfc1bdf70a64aea','/tmp/cunchu/user_1/ae1e9369-50f8-448c-8725-6586f06053e9.txt','ae1e9369-50f8-448c-8725-6586f06053e9.txt','test.txt',18,NULL,'2025-09-06 13:23:16.998996',NULL,1),(7,'text/plain','2025-09-06 12:16:43.770795','2025-09-06 13:23:07.498758',_binary '',NULL,0,'d90ef1651fd9e7563e1a1450a16bd784e9a7e2d3df0f5a798dfc1bdf70a64aea','/tmp/cunchu/user_1/154accc4-05ab-4ba7-9579-0a7cfea38580.txt','154accc4-05ab-4ba7-9579-0a7cfea38580.txt','test.txt',18,NULL,'2025-09-06 13:23:07.499372',NULL,1),(9,'text/plain','2025-09-06 13:40:17.700533','2025-09-06 13:40:22.924355',_binary '',NULL,0,'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855','/tmp/cunchu/user_3/507eaa2c-939d-43bf-a8aa-1a3ff9408147.txt','507eaa2c-939d-43bf-a8aa-1a3ff9408147.txt','新建文本文档.txt',0,NULL,'2025-09-06 13:40:22.924846',NULL,3);
/*!40000 ALTER TABLE `files` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `folders`
--

DROP TABLE IF EXISTS `folders`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `folders` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `create_time` datetime(6) DEFAULT NULL,
  `delete_time` datetime(6) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `is_root` bit(1) NOT NULL,
  `name` varchar(255) NOT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKqcp836dgme9195j0wy9v3b6o3` (`parent_id`),
  KEY `FKc2qooq7m62v6o0c8ptaj3x4cj` (`user_id`),
  CONSTRAINT `FKc2qooq7m62v6o0c8ptaj3x4cj` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`),
  CONSTRAINT `FKqcp836dgme9195j0wy9v3b6o3` FOREIGN KEY (`parent_id`) REFERENCES `folders` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `folders`
--

LOCK TABLES `folders` WRITE;
/*!40000 ALTER TABLE `folders` DISABLE KEYS */;
/*!40000 ALTER TABLE `folders` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `system_config`
--

DROP TABLE IF EXISTS `system_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `config_key` varchar(100) NOT NULL,
  `config_type` varchar(50) DEFAULT NULL,
  `config_value` varchar(1000) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `description` varchar(500) DEFAULT NULL,
  `is_active` bit(1) NOT NULL,
  `is_system` bit(1) NOT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_npsxm1erd0lbetjn5d3ayrsof` (`config_key`),
  KEY `FK94vs8kx7qaa1s2hncds3pwcrc` (`updated_by`),
  CONSTRAINT `FK94vs8kx7qaa1s2hncds3pwcrc` FOREIGN KEY (`updated_by`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `system_config`
--

LOCK TABLES `system_config` WRITE;
/*!40000 ALTER TABLE `system_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user_logs`
--

DROP TABLE IF EXISTS `user_logs`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_logs` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_description` varchar(1000) DEFAULT NULL,
  `action_type` varchar(50) NOT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `error_message` varchar(1000) DEFAULT NULL,
  `execution_time` bigint DEFAULT NULL,
  `ip_address` varchar(45) DEFAULT NULL,
  `resource_id` bigint DEFAULT NULL,
  `resource_name` varchar(255) DEFAULT NULL,
  `resource_type` varchar(50) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `user_agent` varchar(500) DEFAULT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKs5vt3b9fw02oqjm290njxumxq` (`user_id`),
  CONSTRAINT `FKs5vt3b9fw02oqjm290njxumxq` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `user_logs`
--

LOCK TABLES `user_logs` WRITE;
/*!40000 ALTER TABLE `user_logs` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_logs` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `users`
--

DROP TABLE IF EXISTS `users`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `users` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `avatar_url` varchar(255) DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `display_name` varchar(100) DEFAULT NULL,
  `email` varchar(255) NOT NULL,
  `email_verified` bit(1) DEFAULT NULL,
  `enabled` bit(1) NOT NULL,
  `last_login_ip` varchar(255) DEFAULT NULL,
  `last_login_time` datetime(6) DEFAULT NULL,
  `locked` bit(1) NOT NULL,
  `login_attempts` int DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `password_reset_expiry` datetime(6) DEFAULT NULL,
  `password_reset_token` varchar(255) DEFAULT NULL,
  `phone_number` varchar(20) DEFAULT NULL,
  `quota_limit` bigint NOT NULL,
  `quota_used` bigint DEFAULT NULL,
  `role` enum('USER','ADMIN') DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `verification_token` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_6dotkott2kjsp8vw4d0m25fb7` (`email`),
  UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `users`
--

LOCK TABLES `users` WRITE;
/*!40000 ALTER TABLE `users` DISABLE KEYS */;
INSERT INTO `users` VALUES (1,NULL,'2025-09-06 10:13:27.816825','系统管理员','admin@example.com',_binary '\0',_binary '',NULL,'2025-09-06 15:36:00.645758',_binary '\0',0,'$2a$10$T0vHADJwzwEpNjONEinWuuueuZOzGnL4g6iVqQAFrpGX5NSvHDxM6',NULL,NULL,NULL,10737418240,0,'ADMIN','2025-09-06 15:36:00.647144','admin',NULL),(2,NULL,'2025-09-06 10:13:27.991436','测试用户','demo@example.com',_binary '\0',_binary '',NULL,NULL,_binary '\0',0,'$2a$10$cnwn5/MLhI1LyAtZnx/GHOm0jtQpF/4TfDy7rQ3h4eIU4A7eitRAy',NULL,NULL,NULL,1073741824,0,'USER','2025-09-06 10:13:27.991472','demo',NULL),(3,NULL,'2025-09-06 12:40:57.879359','user','111111@qq.com',_binary '\0',_binary '',NULL,'2025-09-06 15:10:37.000941',_binary '\0',0,'$2a$10$VrpaZ/q.Ud6xGCSMSERCOeTJKn4.yA4Y0JmUpXIC3guC/HIzkwgk2',NULL,NULL,NULL,1073741824,0,'USER','2025-09-06 15:10:37.171618','user',NULL);
/*!40000 ALTER TABLE `users` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2025-09-06 15:42:59
