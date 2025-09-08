# 数据库备份信息

## 备份详情
- **备份时间**: 2025-09-06 23:42
- **备份来源**: test07-dev容器内的MySQL数据库
- **备份文件**: enterprise_file_manager_backup.sql
- **文件大小**: 12,495 字节 (约12KB)
- **备份方式**: mysqldump命令行工具

## 数据库信息
- **数据库名称**: enterprise_file_manager
- **数据库版本**: MySQL 8.0.43
- **连接信息**: localhost:3306
- **用户**: filemanager

## 备份的表结构
1. **users** - 用户表
   - 包含3个用户记录：系统管理员、测试用户、普通用户
   - 用户名：admin, demo, user

2. **files** - 文件表
   - 包含文件记录和删除的文件（回收站数据）

3. **folders** - 文件夹表
   - 文件夹结构数据

4. **file_chunks** - 文件分块表
   - 大文件分块存储信息

5. **system_config** - 系统配置表
   - 系统设置参数

6. **user_logs** - 用户日志表
   - 用户操作日志记录

## 备份命令
```bash
mysqldump -h 127.0.0.1 -P 3306 -u filemanager -pfilemanager_password --no-tablespaces --skip-lock-tables enterprise_file_manager > enterprise_file_manager_backup.sql
```

## 数据统计
- 总记录数：包含所有6个表的完整数据
- 用户数：3个
- 文件记录：包含已删除和未删除的文件
- 文件夹记录：完整的文件夹结构
- 系统配置：完整的系统设置

## 恢复方法
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE enterprise_file_manager CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 恢复数据
mysql -u root -p enterprise_file_manager < enterprise_file_manager_backup.sql
```

## 注意事项
1. 备份文件包含完整的表结构和数据
2. 使用了--no-tablespaces和--skip-lock-tables选项避免权限问题
3. 备份文件可以直接用于数据库恢复
4. 包含了回收站中的已删除文件数据
5. 所有用户密码都已加密存储