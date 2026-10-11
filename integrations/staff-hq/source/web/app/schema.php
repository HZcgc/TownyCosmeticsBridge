<?php
declare(strict_types=1);

function installSchema(PDO $pdo): void
{
    $statements = [
        "CREATE TABLE IF NOT EXISTS users (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            email VARCHAR(190) NOT NULL UNIQUE,
            display_name VARCHAR(80) NOT NULL,
            minecraft_uuid CHAR(36) NULL,
            minecraft_username VARCHAR(32) NULL,
            password_hash VARCHAR(255) NOT NULL,
            role VARCHAR(24) NOT NULL DEFAULT 'helper',
            must_change_password TINYINT(1) NOT NULL DEFAULT 1,
            totp_secret_encrypted TEXT NULL,
            totp_enabled TINYINT(1) NOT NULL DEFAULT 0,
            recovery_codes_json TEXT NULL,
            active TINYINT(1) NOT NULL DEFAULT 1,
            session_version INT UNSIGNED NOT NULL DEFAULT 1,
            last_login_at DATETIME NULL,
            created_at DATETIME NOT NULL,
            updated_at DATETIME NOT NULL,
            INDEX idx_users_role (role),
            INDEX idx_users_active (active),
            INDEX idx_users_minecraft (minecraft_username)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS login_attempts (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            identifier_hash CHAR(64) NOT NULL,
            ip_hash CHAR(64) NOT NULL,
            successful TINYINT(1) NOT NULL DEFAULT 0,
            attempted_at DATETIME NOT NULL,
            INDEX idx_login_identifier_time (identifier_hash, attempted_at),
            INDEX idx_login_ip_time (ip_hash, attempted_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS players (
            uuid CHAR(36) PRIMARY KEY,
            username VARCHAR(32) NOT NULL,
            platform VARCHAR(16) NOT NULL DEFAULT 'java',
            first_seen_at DATETIME NULL,
            last_seen_at DATETIME NULL,
            last_server VARCHAR(64) NULL,
            online TINYINT(1) NOT NULL DEFAULT 0,
            playtime_seconds BIGINT UNSIGNED NOT NULL DEFAULT 0,
            balance DECIMAL(16,2) NULL,
            town_name VARCHAR(96) NULL,
            risk_score SMALLINT UNSIGNED NOT NULL DEFAULT 0,
            metadata_json LONGTEXT NULL,
            updated_at DATETIME NOT NULL,
            INDEX idx_players_username (username),
            INDEX idx_players_first_seen (first_seen_at),
            INDEX idx_players_last_seen (last_seen_at),
            INDEX idx_players_risk (risk_score)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS alerts (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            player_uuid CHAR(36) NULL,
            player_name VARCHAR(32) NOT NULL,
            check_name VARCHAR(96) NOT NULL,
            violation_level DECIMAL(10,2) NULL,
            severity VARCHAR(16) NOT NULL DEFAULT 'medium',
            server_name VARCHAR(64) NULL,
            message TEXT NULL,
            evidence_json LONGTEXT NULL,
            status VARCHAR(20) NOT NULL DEFAULT 'open',
            assigned_user_id BIGINT UNSIGNED NULL,
            occurred_at DATETIME NOT NULL,
            reviewed_at DATETIME NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_alerts_player (player_uuid),
            INDEX idx_alerts_occurred (occurred_at),
            INDEX idx_alerts_status_time (status, occurred_at),
            CONSTRAINT fk_alerts_assignee FOREIGN KEY (assigned_user_id) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS cases (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            case_number VARCHAR(24) NOT NULL UNIQUE,
            player_uuid CHAR(36) NULL,
            player_name VARCHAR(32) NOT NULL,
            title VARCHAR(190) NOT NULL,
            description TEXT NOT NULL,
            status VARCHAR(20) NOT NULL DEFAULT 'open',
            priority VARCHAR(16) NOT NULL DEFAULT 'normal',
            created_by BIGINT UNSIGNED NOT NULL,
            assigned_user_id BIGINT UNSIGNED NULL,
            created_at DATETIME NOT NULL,
            updated_at DATETIME NOT NULL,
            resolved_at DATETIME NULL,
            INDEX idx_cases_player (player_uuid),
            INDEX idx_cases_created (created_at),
            INDEX idx_cases_resolved (resolved_at),
            INDEX idx_cases_status (status, updated_at),
            CONSTRAINT fk_cases_creator FOREIGN KEY (created_by) REFERENCES users(id),
            CONSTRAINT fk_cases_assignee FOREIGN KEY (assigned_user_id) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS case_events (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            case_id BIGINT UNSIGNED NOT NULL,
            actor_user_id BIGINT UNSIGNED NULL,
            event_type VARCHAR(32) NOT NULL,
            body TEXT NULL,
            metadata_json LONGTEXT NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_case_events_case (case_id, created_at),
            CONSTRAINT fk_case_events_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
            CONSTRAINT fk_case_events_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS case_players (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            case_id BIGINT UNSIGNED NOT NULL,
            player_uuid CHAR(36) NULL,
            player_name VARCHAR(32) NOT NULL,
            primary_subject TINYINT(1) NOT NULL DEFAULT 0,
            added_at DATETIME NOT NULL,
            UNIQUE KEY uq_case_player_name (case_id, player_name),
            INDEX idx_case_players_uuid (player_uuid),
            CONSTRAINT fk_case_players_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS evidence (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            case_id BIGINT UNSIGNED NOT NULL,
            evidence_type VARCHAR(24) NOT NULL DEFAULT 'link',
            title VARCHAR(190) NOT NULL,
            url TEXT NOT NULL,
            description TEXT NULL,
            added_by BIGINT UNSIGNED NOT NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_evidence_case (case_id),
            CONSTRAINT fk_evidence_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE CASCADE,
            CONSTRAINT fk_evidence_user FOREIGN KEY (added_by) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS punishments (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            case_id BIGINT UNSIGNED NULL,
            player_uuid CHAR(36) NULL,
            player_name VARCHAR(32) NOT NULL,
            punishment_type VARCHAR(24) NOT NULL,
            reason TEXT NOT NULL,
            duration_seconds BIGINT NULL,
            source VARCHAR(32) NOT NULL DEFAULT 'panel',
            external_id VARCHAR(96) NULL,
            issued_by BIGINT UNSIGNED NULL,
            issued_by_label VARCHAR(190) NULL,
            issued_at DATETIME NOT NULL,
            expires_at DATETIME NULL,
            revoked_at DATETIME NULL,
            revoked_by BIGINT UNSIGNED NULL,
            execution_status VARCHAR(24) NOT NULL DEFAULT 'recorded',
            INDEX idx_punishments_player (player_uuid, issued_at),
            INDEX idx_punishments_issued (issued_at),
            INDEX idx_punishments_case (case_id),
            CONSTRAINT fk_punishments_case FOREIGN KEY (case_id) REFERENCES cases(id) ON DELETE SET NULL,
            CONSTRAINT fk_punishments_issuer FOREIGN KEY (issued_by) REFERENCES users(id) ON DELETE SET NULL,
            CONSTRAINT fk_punishments_revoker FOREIGN KEY (revoked_by) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS audit_logs (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            actor_user_id BIGINT UNSIGNED NULL,
            actor_label VARCHAR(190) NULL,
            actor_minecraft_uuid CHAR(36) NULL,
            actor_kind VARCHAR(24) NULL,
            action VARCHAR(96) NOT NULL,
            target_type VARCHAR(48) NULL,
            target_id VARCHAR(96) NULL,
            source_server VARCHAR(64) NULL,
            command_text TEXT NULL,
            ip_hash CHAR(64) NULL,
            metadata_json LONGTEXT NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_audit_actor_time (actor_user_id, created_at),
            INDEX idx_audit_minecraft_time (actor_minecraft_uuid, created_at),
            INDEX idx_audit_created (created_at),
            INDEX idx_audit_action_time (action, created_at),
            CONSTRAINT fk_audit_actor FOREIGN KEY (actor_user_id) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS player_messages (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            event_id VARCHAR(96) NULL,
            player_uuid CHAR(36) NOT NULL,
            player_name VARCHAR(32) NOT NULL,
            channel_name VARCHAR(32) NOT NULL DEFAULT 'global',
            recipient_uuid CHAR(36) NULL,
            recipient_name VARCHAR(96) NULL,
            message_text TEXT NOT NULL,
            source_server VARCHAR(64) NULL,
            origin VARCHAR(24) NULL,
            delivered TINYINT(1) NOT NULL DEFAULT 1,
            sent_at DATETIME NOT NULL,
            UNIQUE KEY uq_player_messages_event (event_id),
            INDEX idx_messages_player_time (player_uuid, sent_at),
            INDEX idx_messages_recipient_time (recipient_uuid, sent_at),
            INDEX idx_messages_channel_time (channel_name, sent_at),
            INDEX idx_messages_server_time (source_server, sent_at),
            INDEX idx_messages_sent (sent_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS api_clients (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            client_name VARCHAR(96) NOT NULL,
            token_prefix CHAR(12) NOT NULL,
            token_hash CHAR(64) NOT NULL UNIQUE,
            active TINYINT(1) NOT NULL DEFAULT 1,
            last_seen_at DATETIME NULL,
            created_at DATETIME NOT NULL,
            rotated_at DATETIME NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS ingest_events (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            api_client_id BIGINT UNSIGNED NOT NULL,
            event_type VARCHAR(64) NOT NULL,
            source_server VARCHAR(64) NULL,
            event_id VARCHAR(96) NULL,
            payload_json LONGTEXT NOT NULL,
            received_at DATETIME NOT NULL,
            UNIQUE KEY uq_ingest_event (api_client_id, event_id),
            INDEX idx_ingest_received (received_at),
            INDEX idx_ingest_type_time (event_type, received_at),
            CONSTRAINT fk_ingest_client FOREIGN KEY (api_client_id) REFERENCES api_clients(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS documents (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            title VARCHAR(190) NOT NULL,
            category VARCHAR(64) NOT NULL DEFAULT 'General',
            body MEDIUMTEXT NOT NULL,
            updated_by BIGINT UNSIGNED NOT NULL,
            created_at DATETIME NOT NULL,
            updated_at DATETIME NOT NULL,
            INDEX idx_documents_category (category, title),
            CONSTRAINT fk_documents_user FOREIGN KEY (updated_by) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS attachments (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            owner_type VARCHAR(24) NOT NULL,
            owner_id BIGINT UNSIGNED NOT NULL,
            original_name VARCHAR(255) NOT NULL,
            stored_path VARCHAR(255) NOT NULL,
            mime_type VARCHAR(127) NOT NULL,
            size_bytes BIGINT UNSIGNED NOT NULL,
            sha256 CHAR(64) NOT NULL,
            uploaded_by BIGINT UNSIGNED NOT NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_attachments_owner (owner_type, owner_id, created_at),
            CONSTRAINT fk_attachments_user FOREIGN KEY (uploaded_by) REFERENCES users(id)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS player_sessions (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            player_uuid CHAR(36) NOT NULL,
            player_name VARCHAR(32) NOT NULL,
            server_name VARCHAR(64) NULL,
            joined_at DATETIME NOT NULL,
            left_at DATETIME NULL,
            duration_seconds BIGINT UNSIGNED NULL,
            created_at DATETIME NOT NULL,
            INDEX idx_sessions_player (player_uuid, joined_at),
            INDEX idx_sessions_joined (joined_at),
            INDEX idx_sessions_server (server_name, joined_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS role_permissions (
            role VARCHAR(24) NOT NULL,
            permission VARCHAR(96) NOT NULL,
            allowed TINYINT(1) NOT NULL DEFAULT 1,
            updated_by BIGINT UNSIGNED NULL,
            updated_at DATETIME NOT NULL,
            PRIMARY KEY (role, permission),
            INDEX idx_role_permissions_allowed (role, allowed),
            CONSTRAINT fk_role_permissions_user FOREIGN KEY (updated_by) REFERENCES users(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS action_queue (
            id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
            punishment_id BIGINT UNSIGNED NULL,
            action_type VARCHAR(24) NOT NULL,
            player_uuid CHAR(36) NULL,
            player_name VARCHAR(32) NOT NULL,
            reason VARCHAR(1000) NOT NULL,
            duration_seconds BIGINT UNSIGNED NULL,
            target_server VARCHAR(64) NOT NULL DEFAULT 'smp',
            requested_by BIGINT UNSIGNED NOT NULL,
            requested_at DATETIME NOT NULL,
            status VARCHAR(24) NOT NULL DEFAULT 'pending',
            claimed_by BIGINT UNSIGNED NULL,
            claimed_at DATETIME NULL,
            completed_at DATETIME NULL,
            result_message VARCHAR(1000) NULL,
            INDEX idx_action_queue_delivery (target_server, status, requested_at),
            INDEX idx_action_queue_punishment (punishment_id),
            CONSTRAINT fk_action_queue_punishment FOREIGN KEY (punishment_id) REFERENCES punishments(id) ON DELETE SET NULL,
            CONSTRAINT fk_action_queue_requester FOREIGN KEY (requested_by) REFERENCES users(id),
            CONSTRAINT fk_action_queue_client FOREIGN KEY (claimed_by) REFERENCES api_clients(id) ON DELETE SET NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS server_status (
            server_name VARCHAR(64) PRIMARY KEY,
            online_players INT UNSIGNED NOT NULL DEFAULT 0,
            max_players INT UNSIGNED NOT NULL DEFAULT 0,
            tps DECIMAL(6,2) NULL,
            mspt DECIMAL(8,2) NULL,
            memory_used_mb INT UNSIGNED NULL,
            memory_max_mb INT UNSIGNED NULL,
            uptime_seconds BIGINT UNSIGNED NULL,
            world_count SMALLINT UNSIGNED NULL,
            plugin_count SMALLINT UNSIGNED NULL,
            minecraft_version VARCHAR(96) NULL,
            java_version VARCHAR(96) NULL,
            metadata_json LONGTEXT NULL,
            captured_at DATETIME NOT NULL,
            updated_at DATETIME NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS server_plugins (
            server_name VARCHAR(64) NOT NULL,
            plugin_name VARCHAR(96) NOT NULL,
            plugin_version VARCHAR(96) NULL,
            enabled TINYINT(1) NOT NULL DEFAULT 1,
            updated_at DATETIME NOT NULL,
            PRIMARY KEY (server_name, plugin_name),
            INDEX idx_server_plugins_enabled (server_name, enabled)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",

        "CREATE TABLE IF NOT EXISTS settings (
            setting_key VARCHAR(96) PRIMARY KEY,
            setting_value LONGTEXT NULL,
            updated_at DATETIME NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci",
    ];

    foreach ($statements as $statement) {
        $pdo->exec($statement);
    }
}

function upgradeSchema(PDO $pdo): void
{
    installSchema($pdo);

    addColumnIfMissing($pdo, 'users', 'minecraft_uuid', 'CHAR(36) NULL AFTER display_name');
    addColumnIfMissing($pdo, 'users', 'minecraft_username', 'VARCHAR(32) NULL AFTER minecraft_uuid');
    addColumnIfMissing($pdo, 'punishments', 'issued_by_label', 'VARCHAR(190) NULL AFTER issued_by');
    addColumnIfMissing($pdo, 'punishments', 'execution_status', "VARCHAR(24) NOT NULL DEFAULT 'recorded' AFTER revoked_by");
    addColumnIfMissing($pdo, 'audit_logs', 'actor_minecraft_uuid', 'CHAR(36) NULL AFTER actor_label');
    addColumnIfMissing($pdo, 'audit_logs', 'actor_kind', 'VARCHAR(24) NULL AFTER actor_minecraft_uuid');
    addColumnIfMissing($pdo, 'audit_logs', 'source_server', 'VARCHAR(64) NULL AFTER target_id');
    addColumnIfMissing($pdo, 'audit_logs', 'command_text', 'TEXT NULL AFTER source_server');
    addIndexIfMissing($pdo, 'users', 'idx_users_minecraft', 'INDEX idx_users_minecraft (minecraft_username)');
    addIndexIfMissing($pdo, 'players', 'idx_players_first_seen', 'INDEX idx_players_first_seen (first_seen_at)');
    addIndexIfMissing($pdo, 'alerts', 'idx_alerts_occurred', 'INDEX idx_alerts_occurred (occurred_at)');
    addIndexIfMissing($pdo, 'cases', 'idx_cases_created', 'INDEX idx_cases_created (created_at)');
    addIndexIfMissing($pdo, 'cases', 'idx_cases_resolved', 'INDEX idx_cases_resolved (resolved_at)');
    addIndexIfMissing($pdo, 'punishments', 'idx_punishments_issued', 'INDEX idx_punishments_issued (issued_at)');
    addIndexIfMissing($pdo, 'audit_logs', 'idx_audit_created', 'INDEX idx_audit_created (created_at)');
    addIndexIfMissing($pdo, 'audit_logs', 'idx_audit_minecraft_time', 'INDEX idx_audit_minecraft_time (actor_minecraft_uuid, created_at)');
    addIndexIfMissing($pdo, 'ingest_events', 'idx_ingest_received', 'INDEX idx_ingest_received (received_at)');

    $pdo->exec(
        "INSERT IGNORE INTO case_players (case_id, player_uuid, player_name, primary_subject, added_at)
         SELECT id, player_uuid, player_name, 1, created_at FROM cases WHERE player_name <> ''"
    );

    seedRolePermissions($pdo);
    $pdo->exec(
        "UPDATE punishments p
         JOIN players pl ON LOWER(pl.username) = LOWER(p.player_name)
         SET p.player_uuid = pl.uuid
         WHERE p.player_uuid IS NULL OR p.player_uuid = ''"
    );
    $pdo->exec(
        "UPDATE alerts a
         JOIN players pl ON LOWER(pl.username) = LOWER(a.player_name)
         SET a.player_uuid = pl.uuid
         WHERE a.player_uuid IS NULL OR a.player_uuid = ''"
    );

    $statement = $pdo->prepare(
        "INSERT INTO settings (setting_key, setting_value, updated_at) VALUES ('schema.version', '5', ?)
         ON DUPLICATE KEY UPDATE setting_value = VALUES(setting_value), updated_at = VALUES(updated_at)"
    );
    $statement->execute([date('Y-m-d H:i:s')]);
}

function seedRolePermissions(PDO $pdo): void
{
    $defaults = [
        'admin' => [
            'dashboard.view', 'analytics.view', 'players.view', 'alerts.view', 'alerts.manage',
            'cases.view', 'cases.create', 'cases.manage', 'punishments.view', 'punishments.create',
            'punishments.execute', 'documents.view', 'documents.manage', 'team.view', 'team.manage',
            'commands.view', 'messages.view', 'audit.view', 'api.manage',
        ],
        'moderator' => [
            'dashboard.view', 'analytics.view', 'players.view', 'alerts.view', 'alerts.manage',
            'cases.view', 'cases.create', 'cases.manage', 'punishments.view', 'punishments.create',
            'punishments.execute', 'documents.view', 'commands.view', 'messages.view',
        ],
        'helper' => [
            'dashboard.view', 'analytics.view', 'players.view', 'alerts.view', 'cases.view',
            'cases.create', 'documents.view',
        ],
    ];
    $insert = $pdo->prepare(
        'INSERT IGNORE INTO role_permissions (role, permission, allowed, updated_by, updated_at) VALUES (?, ?, 1, NULL, ?)'
    );
    foreach ($defaults as $role => $permissions) {
        foreach ($permissions as $permission) {
            $insert->execute([$role, $permission, date('Y-m-d H:i:s')]);
        }
    }
}

function addColumnIfMissing(PDO $pdo, string $table, string $column, string $definition): void
{
    $statement = $pdo->prepare(
        'SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND COLUMN_NAME = ?'
    );
    $statement->execute([$table, $column]);
    if ((int) $statement->fetchColumn() === 0) {
        $pdo->exec(sprintf('ALTER TABLE `%s` ADD COLUMN `%s` %s', $table, $column, $definition));
    }
}

function addIndexIfMissing(PDO $pdo, string $table, string $index, string $definition): void
{
    $statement = $pdo->prepare(
        'SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ?'
    );
    $statement->execute([$table, $index]);
    if ((int) $statement->fetchColumn() === 0) {
        $pdo->exec(sprintf('ALTER TABLE `%s` ADD %s', $table, $definition));
    }
}
