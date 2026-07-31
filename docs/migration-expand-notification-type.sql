-- ============================================================
--  Migration: Expand NOTIFICATION.Type CHECK constraint
--  Purpose:  Add 6 new notification types (new_user, user_report,
--            sync_completed, sync_failed, system_alert, content_alert)
--            that were added to Java NotificationType enum but
--            never allowed in the DB constraint.
--  Run:      Execute this script against your SQL Server database.
--  Date:     2026-08-01
-- ============================================================

DECLARE @ConstraintName NVARCHAR(200);

-- Find the existing CHECK constraint on NOTIFICATION.Type column
SELECT @ConstraintName = name
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('NOTIFICATION')
  AND parent_column_id = COLUMNPROPERTY(OBJECT_ID('NOTIFICATION'), 'Type', 'ColumnId');

IF @ConstraintName IS NOT NULL
BEGIN
    DECLARE @DropSql NVARCHAR(MAX) = 'ALTER TABLE [NOTIFICATION] DROP CONSTRAINT [' + @ConstraintName + ']';
    EXEC sp_executesql @DropSql;
    PRINT 'Dropped constraint: ' + @ConstraintName;
END
ELSE
BEGIN
    PRINT 'No CHECK constraint found on NOTIFICATION.Type — nothing to drop.';
END

-- Re-create the constraint with all 10 notification types
ALTER TABLE [NOTIFICATION] ADD CONSTRAINT [CK_NOTIFICATION_Type]
    CHECK ([Type] IN (
        'new_paper',
        'trend_alert',
        'system',
        'upgrade_prompt',
        'new_user',
        'user_report',
        'sync_completed',
        'sync_failed',
        'system_alert',
        'content_alert'
    ));

PRINT 'Migration applied: NOTIFICATION.Type constraint expanded to 10 values.';
