-- ============================================
-- SCITRACK — Tao tai khoan Admin
-- Email: admin@tester.com
-- Pass:  123
-- ============================================

-- 1. Tao Role ADMIN neu chua co
IF NOT EXISTS (SELECT 1 FROM [ROLE] WHERE RoleName = 'admin')
BEGIN
    INSERT INTO [ROLE] (RoleID, RoleName, Description, IsSystem) VALUES
        ('A0000001-0000-0000-0000-000000000001', 'admin', N'System Administrator', 1);
END
GO

-- 2. Tao tai khoan admin@tester.com / 123
IF NOT EXISTS (SELECT 1 FROM [USER] WHERE Email = 'admin@tester.com')
BEGIN
    INSERT INTO [USER] (UserID, RoleID, Email, PasswordHash, FullName, IsActive) VALUES
        ('BBCFFE65-C57E-43CD-8A8B-A072B11C6E9B',
         'A0000001-0000-0000-0000-000000000001',
         'admin@tester.com',
         '$2b$12$gMs9na4ZqloS73Nl2gotPeQL0wb0kOhUb15LuNwSLdyKAuF8N9sJq',
         N'Admin Tester',
         1);
    PRINT '>> [OK] Da tao tai khoan admin@tester.com / 123';
END
ELSE
BEGIN
    -- Update password neu tai khoan da ton tai
    UPDATE [USER]
    SET PasswordHash = '$2b$12$gMs9na4ZqloS73Nl2gotPeQL0wb0kOhUb15LuNwSLdyKAuF8N9sJq',
        RoleID = 'A0000001-0000-0000-0000-000000000001',
        IsActive = 1
    WHERE Email = 'admin@tester.com';
    PRINT '>> [OK] Da cap nhat mat khau admin@tester.com / 123';
END
GO
