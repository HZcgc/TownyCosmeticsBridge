<?php
declare(strict_types=1);

/** Apply the requested role configuration once, retaining a rollback snapshot. */
function hqRolePresetMigration(PDO $pdo): void
{
    $pdo->beginTransaction();
    try {
        $applied = $pdo->query("SELECT setting_value FROM settings WHERE setting_key='staff.roles.preset_5_1_1'")->fetchColumn();
        if (!$applied) {
            $before = $pdo->query("SELECT role,permission,allowed FROM role_permissions WHERE role IN ('helper','moderator','admin') ORDER BY role,permission")->fetchAll();
            $pdo->prepare('INSERT IGNORE INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?)')
                ->execute(['staff.roles.before_5_1_1',jsonEncode($before),now()]);
            $put = $pdo->prepare('INSERT INTO role_permissions(role,permission,allowed,updated_at) VALUES(?,?,?,?) ON DUPLICATE KEY UPDATE allowed=VALUES(allowed),updated_at=VALUES(updated_at)');
            foreach (['helper','moderator','admin'] as $role) {
                $allowed = defaultRolePermissions($role);
                foreach (permissionCatalog() as $permission => $_) {
                    $put->execute([$role,$permission,(int)in_array($permission,$allowed,true),now()]);
                }
            }
            $pdo->prepare('INSERT INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?)')
                ->execute(['staff.roles.preset_5_1_1','applied',now()]);
        }
        $pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','10',?) ON DUPLICATE KEY UPDATE setting_value='10',updated_at=VALUES(updated_at)")->execute([now()]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }
}

/** A persistent ID links the guide to the normal Documents editor, even after renaming. */
function hqStaffGuideId(): int
{
    $user = requirePermission('documents.view');
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $put = $pdo->prepare('INSERT IGNORE INTO settings(setting_key,setting_value,updated_at) VALUES(?,?,?)');
        $put->execute(['staff.guide.document_id','pending',now()]);
        $select = $pdo->prepare('SELECT setting_value FROM settings WHERE setting_key=? FOR UPDATE');
        $select->execute(['staff.guide.document_id']);
        $value = (string)$select->fetchColumn();
        if ($value !== 'pending') {
            $pdo->commit();
            return (int)$value;
        }
        $existing = $pdo->prepare('SELECT id FROM documents WHERE title=? ORDER BY id LIMIT 1');
        $existing->execute(['Staff field guide']);
        $id = (int)$existing->fetchColumn();
        if (!$id) {
            $body = file_get_contents(APP_ROOT.'/app/staff-guide.txt');
            if ($body === false || trim($body) === '') throw new RuntimeException('Staff guide template is unavailable.');
            $owner = (int)$pdo->query("SELECT id FROM users WHERE role='owner' ORDER BY id LIMIT 1")->fetchColumn();
            $pdo->prepare('INSERT INTO documents(title,category,body,updated_by,created_at,updated_at) VALUES(?,?,?,?,?,?)')
                ->execute(['Staff field guide','Staff onboarding',$body,$owner ?: (int)$user['id'],now(),now()]);
            $id = (int)$pdo->lastInsertId();
        }
        $pdo->prepare('UPDATE settings SET setting_value=?,updated_at=? WHERE setting_key=?')
            ->execute([(string)$id,now(),'staff.guide.document_id']);
        $pdo->commit();
        return $id;
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }
}
