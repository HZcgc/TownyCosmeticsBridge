<?php
declare(strict_types=1);

function ensureSchemaCurrent(PDO $pdo): void
{
    $version=(int)($pdo->query("SELECT setting_value FROM settings WHERE setting_key='schema.version'")->fetchColumn()?:0);
    if($version>=10)return;
    if((int)$pdo->query("SELECT GET_LOCK('townyreborn.hq.schema',30)")->fetchColumn()!==1)throw new RuntimeException('Schema upgrade is busy; retry shortly.');
    try {
        $version=(int)($pdo->query("SELECT setting_value FROM settings WHERE setting_key='schema.version'")->fetchColumn()?:0);
        if($version<5)upgradeSchema($pdo);
        if($version<6){observabilitySchema($pdo);$pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','6',?) ON DUPLICATE KEY UPDATE setting_value='6',updated_at=VALUES(updated_at)")->execute([now()]);}
        if($version<7){analyticsSchema($pdo);$pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','7',?) ON DUPLICATE KEY UPDATE setting_value='7',updated_at=VALUES(updated_at)")->execute([now()]);}
        if($version<8){notificationSchema($pdo);$pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','8',?) ON DUPLICATE KEY UPDATE setting_value='8',updated_at=VALUES(updated_at)")->execute([now()]);}
        if($version<9){workspaceSchema($pdo);$pdo->prepare("INSERT INTO settings(setting_key,setting_value,updated_at) VALUES('schema.version','9',?) ON DUPLICATE KEY UPDATE setting_value='9',updated_at=VALUES(updated_at)")->execute([now()]);}
        if($version<10)hqRolePresetMigration($pdo);
    } finally { $pdo->query("SELECT RELEASE_LOCK('townyreborn.hq.schema')"); }
}

function normalizeUuid(?string $uuid): ?string
{
    $compact = strtolower(preg_replace('/[^0-9a-f]/i', '', (string) $uuid) ?? '');
    if (!preg_match('/^[0-9a-f]{32}$/', $compact)) {
        return null;
    }
    return substr($compact, 0, 8) . '-' . substr($compact, 8, 4) . '-' . substr($compact, 12, 4)
        . '-' . substr($compact, 16, 4) . '-' . substr($compact, 20, 12);
}

/** @return array<string,mixed>|null */
function resolvePlayer(string $name = '', ?string $uuid = null): ?array
{
    $normalized = normalizeUuid($uuid);
    if ($normalized !== null) {
        $statement = db()->prepare('SELECT * FROM players WHERE uuid = ? LIMIT 1');
        $statement->execute([$normalized]);
        $player = $statement->fetch();
        if ($player) {
            return $player;
        }
    }
    $name = trim($name);
    if ($name !== '') {
        $statement = db()->prepare('SELECT * FROM players WHERE LOWER(username) = LOWER(?) ORDER BY updated_at DESC LIMIT 1');
        $statement->execute([$name]);
        $player = $statement->fetch();
        if ($player) {
            return $player;
        }
    }
    return null;
}

/** @return list<array{uuid:?string,username:string,platform:string,online:bool}> */
function postedPlayers(string $field = 'case_players_json'): array
{
    $decoded = json_decode((string) ($_POST[$field] ?? '[]'), true);
    if (!is_array($decoded) || !array_is_list($decoded)) {
        throw new RuntimeException('Select players from the search results.');
    }
    $players = [];
    $seen = [];
    foreach (array_slice($decoded, 0, 20) as $item) {
        if (!is_array($item)) continue;
        $candidate = resolvePlayer((string) ($item['username'] ?? ''), (string) ($item['uuid'] ?? ''));
        if (!$candidate) continue;
        $uuid = normalizeUuid((string) $candidate['uuid']);
        $key = $uuid ?: strtolower((string) $candidate['username']);
        if (isset($seen[$key])) continue;
        $seen[$key] = true;
        $players[] = [
            'uuid' => $uuid,
            'username' => mb_substr((string) $candidate['username'], 0, 32),
            'platform' => (string) ($candidate['platform'] ?? 'java'),
            'online' => (bool) ($candidate['online'] ?? false),
        ];
    }
    return $players;
}

/** @param list<array{uuid:?string,username:string}> $players */
function addPlayersToCase(int $caseId, array $players, bool $firstIsPrimary = false): void
{
    if ($caseId <= 0 || $players === []) return;
    $statement = db()->prepare(
        'INSERT INTO case_players (case_id, player_uuid, player_name, primary_subject, added_at)
         VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE player_uuid = VALUES(player_uuid)'
    );
    foreach ($players as $index => $player) {
        $statement->execute([
            $caseId,
            normalizeUuid($player['uuid'] ?? null),
            mb_substr((string) $player['username'], 0, 32),
            $firstIsPrimary && $index === 0 ? 1 : 0,
            now(),
        ]);
    }
}

/** @return list<array<string,mixed>> */
function casePlayers(int $caseId): array
{
    $statement = db()->prepare('SELECT * FROM case_players WHERE case_id = ? ORDER BY primary_subject DESC, id ASC');
    $statement->execute([$caseId]);
    return $statement->fetchAll();
}

/** @param list<int> $caseIds @return array<int,list<array<string,mixed>>> */
function casePlayersMap(array $caseIds): array
{
    $caseIds = array_values(array_unique(array_filter(array_map('intval', $caseIds), static fn(int $id): bool => $id > 0)));
    if ($caseIds === []) return [];
    $placeholders = implode(',', array_fill(0, count($caseIds), '?'));
    $statement = db()->prepare("SELECT * FROM case_players WHERE case_id IN ($placeholders) ORDER BY primary_subject DESC, id ASC");
    $statement->execute($caseIds);
    $map = [];
    foreach ($statement->fetchAll() as $row) {
        $map[(int) $row['case_id']][] = $row;
    }
    return $map;
}

function avatarUrl(?string $uuid, string $username, int $size = 48): string
{
    return 'avatar.php?' . http_build_query([
        'v' => 2,
        'uuid' => normalizeUuid($uuid),
        'name' => mb_substr(trim($username), 0, 32),
        'size' => max(24, min(128, $size)),
    ]);
}

function playerProfileUrl(?string $uuid, string $username): string
{
    $params = ['page' => 'player'];
    $normalized = normalizeUuid($uuid);
    if ($normalized !== null) $params['uuid'] = $normalized;
    else $params['name'] = trim($username);
    return 'index.php?' . http_build_query($params);
}

function renderPlayerIdentity(string $username, ?string $uuid = null, ?string $subtitle = null, bool $small = false): string
{
    $username = trim($username) ?: 'Unknown player';
    $normalizedUuid = normalizeUuid($uuid);
    $subtitle ??= $normalizedUuid ?: 'UUID unavailable';
    $class = $small ? ' player-identity small' : ' player-identity';
    $size = $small ? 32 : 42;
    return '<a class="' . trim($class) . ' player-link" href="' . e(playerProfileUrl($normalizedUuid, $username)) . '"><img class="skin-avatar" loading="lazy" src="' . e(avatarUrl($normalizedUuid, $username, $size))
        . '" data-player-avatar data-avatar-name="' . e($username) . '" data-avatar-uuid="' . e($normalizedUuid ?? '')
        . '" data-avatar-size="' . $size . '" alt="' . e($username . ' Minecraft skin') . '"><span><strong>'
        . e($username) . '</strong><small>' . e($subtitle) . '</small></span></a>';
}

/** @return array<string,string> */
function allowedAttachmentTypes(): array
{
    return [
        'image/jpeg' => 'jpg', 'image/png' => 'png', 'image/gif' => 'gif', 'image/webp' => 'webp',
        'video/mp4' => 'mp4', 'video/webm' => 'webm', 'video/quicktime' => 'mov',
        'audio/mpeg' => 'mp3', 'audio/ogg' => 'ogg', 'audio/wav' => 'wav', 'audio/x-wav' => 'wav',
        'application/pdf' => 'pdf', 'text/plain' => 'txt', 'application/json' => 'json',
        'application/zip' => 'zip', 'application/x-zip-compressed' => 'zip',
        'application/vnd.openxmlformats-officedocument.wordprocessingml.document' => 'docx',
    ];
}

function linkedStaffIdentity(array $staff): string
{
    $minecraftName = trim((string) ($staff['minecraft_username'] ?? ''));
    if ($minecraftName !== '') {
        return renderPlayerIdentity($minecraftName, (string) ($staff['minecraft_uuid'] ?? ''), (string) $staff['display_name'], true);
    }
    $letter = strtoupper(mb_substr((string) ($staff['display_name'] ?? '?'), 0, 1));
    return '<span class="player-identity small"><span class="fallback-avatar">' . e($letter) . '</span><span><strong>'
        . e($staff['display_name'] ?? 'Staff') . '</strong><small>Not linked</small></span></span>';
}

/** @return list<array<string,mixed>> */
function attachmentsFor(string $ownerType, int $ownerId): array
{
    $statement = db()->prepare(
        'SELECT a.*, u.display_name FROM attachments a JOIN users u ON u.id = a.uploaded_by
         WHERE a.owner_type = ? AND a.owner_id = ? ORDER BY a.created_at DESC'
    );
    $statement->execute([$ownerType, $ownerId]);
    return $statement->fetchAll();
}

function storeUploadedAttachments(string $ownerType, int $ownerId, int $userId, string $field = 'attachments'): int
{
    if (!in_array($ownerType, ['case', 'document', 'player'], true) || $ownerId <= 0) {
        throw new RuntimeException('Invalid attachment destination.');
    }
    $files = normalizedUploadList($field);
    if ($files === []) return 0;

    $allowed = allowedAttachmentTypes();
    $finfo = new finfo(FILEINFO_MIME_TYPE);
    $relativeDirectory = 'storage/uploads/' . date('Y/m');
    $absoluteDirectory = APP_ROOT . '/' . $relativeDirectory;
    if (!is_dir($absoluteDirectory) && !mkdir($absoluteDirectory, 0750, true) && !is_dir($absoluteDirectory)) {
        throw new RuntimeException('The upload directory could not be created.');
    }

    $insert = db()->prepare(
        'INSERT INTO attachments (owner_type, owner_id, original_name, stored_path, mime_type, size_bytes, sha256, uploaded_by, created_at)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $stored = [];
    $insertedIds = [];
    try {
        foreach ($files as $file) {
            $error = (int) $file['error'];
            if ($error === UPLOAD_ERR_NO_FILE) continue;
            if ($error !== UPLOAD_ERR_OK) throw new RuntimeException(uploadErrorMessage($error));
            $size = (int) $file['size'];
            if ($size < 1) throw new RuntimeException('Empty files cannot be uploaded.');
            if ($ownerType==='player' && $size>104857600) throw new RuntimeException('Profile attachments are limited to 100 MB per file.');
            $temporary = (string) $file['tmp_name'];
            if (!is_uploaded_file($temporary)) throw new RuntimeException('The upload could not be verified.');
            $mime = (string) $finfo->file($temporary);
            if (!isset($allowed[$mime])) throw new RuntimeException('Unsupported file type: ' . $mime . '.');
            $storedName = bin2hex(random_bytes(24)) . '.' . $allowed[$mime];
            $relativePath = $relativeDirectory . '/' . $storedName;
            $absolutePath = APP_ROOT . '/' . $relativePath;
            if (!move_uploaded_file($temporary, $absolutePath)) throw new RuntimeException('The attachment could not be stored.');
            @chmod($absolutePath, 0640);
            $stored[] = $absolutePath;
            $original = trim((string) $file['name']) ?: 'attachment.' . $allowed[$mime];
            $insert->execute([
                $ownerType, $ownerId, mb_substr(basename($original), 0, 255), $relativePath, $mime, $size,
                hash_file('sha256', $absolutePath), $userId, now(),
            ]);
            $insertedIds[] = (int) db()->lastInsertId();
        }
    } catch (Throwable $exception) {
        if ($insertedIds !== []) {
            $placeholders = implode(',', array_fill(0, count($insertedIds), '?'));
            db()->prepare("DELETE FROM attachments WHERE id IN ($placeholders)")->execute($insertedIds);
        }
        foreach ($stored as $path) {
            if (is_file($path)) @unlink($path);
        }
        throw $exception;
    }
    return count($stored);
}

/** @return list<array{name:string,tmp_name:string,error:int,size:int}> */
function normalizedUploadList(string $field): array
{
    if (!isset($_FILES[$field]) || !is_array($_FILES[$field])) return [];
    $upload = $_FILES[$field];
    if (!is_array($upload['name'] ?? null)) {
        return [[
            'name' => (string) ($upload['name'] ?? ''), 'tmp_name' => (string) ($upload['tmp_name'] ?? ''),
            'error' => (int) ($upload['error'] ?? UPLOAD_ERR_NO_FILE), 'size' => (int) ($upload['size'] ?? 0),
        ]];
    }
    $files = [];
    foreach ($upload['name'] as $index => $name) {
        $files[] = [
            'name' => (string) $name, 'tmp_name' => (string) ($upload['tmp_name'][$index] ?? ''),
            'error' => (int) ($upload['error'][$index] ?? UPLOAD_ERR_NO_FILE), 'size' => (int) ($upload['size'][$index] ?? 0),
        ];
    }
    return $files;
}

function uploadErrorMessage(int $error): string
{
    return match ($error) {
        UPLOAD_ERR_INI_SIZE, UPLOAD_ERR_FORM_SIZE => 'The webserver rejected the file before Staff HQ received it (effective PHP per-file limit: '
            . (string) ini_get('upload_max_filesize') . '). Staff HQ has no application upload cap; raise the PHP/Plesk limit.',
        UPLOAD_ERR_PARTIAL => 'The file upload was interrupted.',
        UPLOAD_ERR_NO_TMP_DIR => 'The webserver has no temporary upload directory.',
        UPLOAD_ERR_CANT_WRITE => 'The webserver could not write the uploaded file.',
        default => 'The upload failed with error code ' . $error . '.',
    };
}

function deleteAttachmentRecord(int $attachmentId, ?string $expectedOwner = null, ?int $expectedOwnerId = null): bool
{
    $statement = db()->prepare('SELECT * FROM attachments WHERE id = ? LIMIT 1');
    $statement->execute([$attachmentId]);
    $row = $statement->fetch();
    if (!$row) return false;
    if ($expectedOwner !== null && (string) $row['owner_type'] !== $expectedOwner) return false;
    if ($expectedOwnerId !== null && (int) $row['owner_id'] !== $expectedOwnerId) return false;
    db()->prepare('DELETE FROM attachments WHERE id = ?')->execute([$attachmentId]);
    $absolute = APP_ROOT . '/' . ltrim((string) $row['stored_path'], '/');
    $uploadRoot = realpath(APP_ROOT . '/storage/uploads');
    $file = realpath($absolute);
    if ($uploadRoot !== false && $file !== false && str_starts_with($file, $uploadRoot . DIRECTORY_SEPARATOR) && is_file($file)) {
        @unlink($file);
    }
    return true;
}

function deleteOwnerAttachments(string $ownerType, int $ownerId): void
{
    foreach (attachmentsFor($ownerType, $ownerId) as $attachment) {
        deleteAttachmentRecord((int) $attachment['id'], $ownerType, $ownerId);
    }
}

function formatBytes(int $bytes): string
{
    if ($bytes >= 1_073_741_824) return number_format($bytes / 1_073_741_824, 1) . ' GB';
    if ($bytes >= 1_048_576) return number_format($bytes / 1_048_576, 1) . ' MB';
    if ($bytes >= 1_024) return number_format($bytes / 1_024, 1) . ' KB';
    return $bytes . ' B';
}

function extractDocxText(string $path): string
{
    if (!class_exists('ZipArchive')) throw new RuntimeException('DOCX import needs the PHP Zip extension enabled in Plesk.');
    $zip = new ZipArchive();
    if ($zip->open($path) !== true) throw new RuntimeException('The DOCX file could not be opened.');
    $xml = $zip->getFromName('word/document.xml');
    $zip->close();
    if (!is_string($xml) || $xml === '') throw new RuntimeException('The DOCX file has no readable document body.');

    $document = new DOMDocument();
    $previous = libxml_use_internal_errors(true);
    $loaded = $document->loadXML($xml, LIBXML_NONET | LIBXML_NOERROR | LIBXML_NOWARNING);
    libxml_clear_errors();
    libxml_use_internal_errors($previous);
    if (!$loaded) throw new RuntimeException('The DOCX document XML is invalid.');
    $xpath = new DOMXPath($document);
    $xpath->registerNamespace('w', 'http://schemas.openxmlformats.org/wordprocessingml/2006/main');
    $lines = [];
    foreach ($xpath->query('//w:body/w:p') ?: [] as $paragraph) {
        $text = '';
        foreach ($xpath->query('.//w:t', $paragraph) ?: [] as $node) $text .= $node->textContent;
        $text = trim($text);
        if ($text === '') {
            if ($lines !== [] && end($lines) !== '') $lines[] = '';
            continue;
        }
        $style = '';
        $styleNodes = $xpath->query('./w:pPr/w:pStyle', $paragraph);
        $styleNode = $styleNodes instanceof DOMNodeList ? $styleNodes->item(0) : null;
        if ($styleNode instanceof DOMElement) $style = (string) $styleNode->getAttributeNS('http://schemas.openxmlformats.org/wordprocessingml/2006/main', 'val');
        if (preg_match('/heading\s*([1-6])/i', $style, $match)) $text = str_repeat('#', (int) $match[1]) . ' ' . $text;
        else {
            $numberingNodes = $xpath->query('./w:pPr/w:numPr', $paragraph);
            if ($numberingNodes instanceof DOMNodeList && $numberingNodes->length > 0) $text = '- ' . $text;
        }
        $lines[] = $text;
    }
    $result = trim(preg_replace("/\n{3,}/", "\n\n", implode("\n", $lines)) ?? '');
    if ($result === '') throw new RuntimeException('No readable text was found in the DOCX file.');
    return $result;
}

/** @return array{path:string,title:string,temporary:bool} */
function obtainDocumentImport(): array
{
    $url = trim((string) ($_POST['google_docs_url'] ?? ''));
    if ($url !== '') {
        if (!preg_match('#^https://docs\.google\.com/document/d/([A-Za-z0-9_-]+)#', $url, $match)) {
            throw new RuntimeException('Use a valid Google Docs document URL. The document must allow link access.');
        }
        if (!function_exists('curl_init')) throw new RuntimeException('Google Docs import needs the PHP cURL extension.');
        $temporary = tempnam(sys_get_temp_dir(), 'tr-docx-');
        if ($temporary === false) throw new RuntimeException('A temporary import file could not be created.');
        $handle = fopen($temporary, 'wb');
        if ($handle === false) throw new RuntimeException('A temporary import file could not be opened.');
        $downloaded = 0;
        $curl = curl_init('https://docs.google.com/document/d/' . rawurlencode($match[1]) . '/export?format=docx');
        curl_setopt_array($curl, [
            CURLOPT_FOLLOWLOCATION => true, CURLOPT_MAXREDIRS => 3, CURLOPT_CONNECTTIMEOUT => 8,
            CURLOPT_TIMEOUT => 30, CURLOPT_USERAGENT => 'TownyRebornStaffHQ/3.0',
            CURLOPT_WRITEFUNCTION => static function ($curlHandle, string $data) use ($handle, &$downloaded): int {
                $downloaded += strlen($data);
                if ($downloaded > 104_857_600) return 0;
                return (int) fwrite($handle, $data);
            },
        ]);
        $ok = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        curl_close($curl);
        fclose($handle);
        if (!$ok || $status < 200 || $status >= 300 || $downloaded < 1) {
            @unlink($temporary);
            throw new RuntimeException('Google Docs could not export this document. Enable link access and try again.');
        }
        return ['path' => $temporary, 'title' => 'Imported Google Document', 'temporary' => true];
    }

    $files = normalizedUploadList('import_file');
    $file = $files[0] ?? null;
    if (!$file || (int) $file['error'] !== UPLOAD_ERR_OK || !is_uploaded_file((string) $file['tmp_name'])) {
        throw new RuntimeException($file ? uploadErrorMessage((int) $file['error']) : 'Choose a DOCX, Markdown or text file, or enter a Google Docs URL.');
    }
    return ['path' => (string) $file['tmp_name'], 'title' => pathinfo((string) $file['name'], PATHINFO_FILENAME) ?: 'Imported document', 'temporary' => false];
}

/** @param list<array<string,mixed>> $attachments */
function renderAttachmentGallery(array $attachments, bool $canDelete, string $returnPage, array $returnParams = []): void
{
    if ($attachments === []) {
        echo '<div class="empty-state compact"><strong>No files uploaded</strong><p>Images, videos, PDFs, logs and archives can be attached here.</p></div>';
        return;
    }
    echo '<div class="attachment-grid">';
    foreach ($attachments as $attachment) {
        $id = (int) $attachment['id'];
        $mime = (string) $attachment['mime_type'];
        $inlineUrl = 'media.php?id=' . $id . '&inline=1';
        echo '<article class="attachment-card">';
        if (str_starts_with($mime, 'image/')) {
            echo '<a href="' . e($inlineUrl) . '" target="_blank"><img loading="lazy" src="' . e($inlineUrl) . '" alt=""></a>';
        } elseif (str_starts_with($mime, 'video/')) {
            echo '<video controls preload="metadata" src="' . e($inlineUrl) . '"></video>';
        } elseif (str_starts_with($mime, 'audio/')) {
            echo '<audio controls preload="metadata" src="' . e($inlineUrl) . '"></audio>';
        } else {
            echo '<a class="file-preview" href="media.php?id=' . $id . '" target="_blank">' . e(strtoupper(pathinfo((string) $attachment['original_name'], PATHINFO_EXTENSION) ?: 'FILE')) . '</a>';
        }
        echo '<div class="attachment-meta"><strong>' . e($attachment['original_name']) . '</strong><small>'
            . e(formatBytes((int) $attachment['size_bytes'])) . ' · ' . e(displayDate($attachment['created_at'])) . '</small></div>';
        if ($canDelete) {
            echo '<form method="post" action="index.php?' . e(http_build_query(array_merge(['page' => $returnPage], $returnParams))) . '">'
                . csrfField() . '<input type="hidden" name="action" value="delete-attachment"><input type="hidden" name="attachment_id" value="' . $id . '">'
                . '<input type="hidden" name="return_page" value="' . e($returnPage) . '">';
            foreach ($returnParams as $key => $value) echo '<input type="hidden" name="' . e((string) $key) . '" value="' . e((string) $value) . '">';
            echo '<button class="button danger tiny" data-confirm="Delete this uploaded file permanently?" type="submit">Delete</button></form>';
        }
        echo '</article>';
    }
    echo '</div>';
}

function statScalar(string $sql, array $parameters = []): float
{
    $statement = db()->prepare($sql);
    $statement->execute($parameters);
    return (float) ($statement->fetchColumn() ?: 0);
}

function percent(float $part, float $whole): float
{
    return $whole > 0 ? round(($part / $whole) * 100, 1) : 0.0;
}

/** @return array<string,mixed> */
function legacyAnalyticsSnapshot(int $days = 30): array
{
    $days = in_array($days, [7, 30, 90], true) ? $days : 30;
    $interval = (string) $days;
    $eligibleD1 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 90 DAY) AND DATE_SUB(NOW(), INTERVAL 1 DAY)");
    $retainedD1 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 90 DAY) AND DATE_SUB(NOW(), INTERVAL 1 DAY) AND TIMESTAMPDIFF(HOUR, first_seen_at, last_seen_at) >= 24");
    $eligibleD7 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 180 DAY) AND DATE_SUB(NOW(), INTERVAL 7 DAY)");
    $retainedD7 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 180 DAY) AND DATE_SUB(NOW(), INTERVAL 7 DAY) AND TIMESTAMPDIFF(HOUR, first_seen_at, last_seen_at) >= 168");
    $eligibleD3 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 120 DAY) AND DATE_SUB(NOW(), INTERVAL 3 DAY)");
    $retainedD3 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at BETWEEN DATE_SUB(NOW(), INTERVAL 120 DAY) AND DATE_SUB(NOW(), INTERVAL 3 DAY) AND TIMESTAMPDIFF(HOUR, first_seen_at, last_seen_at) >= 72");
    $eligibleD30 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at <= DATE_SUB(NOW(), INTERVAL 30 DAY)");
    $retainedD30 = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at <= DATE_SUB(NOW(), INTERVAL 30 DAY) AND TIMESTAMPDIFF(HOUR, first_seen_at, last_seen_at) >= 720");
    $active = statScalar("SELECT COUNT(*) FROM players WHERE last_seen_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $returning = statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at < DATE_SUB(NOW(), INTERVAL $interval DAY) AND last_seen_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $sessions = statScalar("SELECT COUNT(*) FROM player_sessions WHERE joined_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $knownPlayers = statScalar('SELECT COUNT(*) FROM players');
    $townMembers = statScalar("SELECT COUNT(*) FROM players WHERE town_name IS NOT NULL AND town_name <> ''");
    $alertsInPeriod = statScalar("SELECT COUNT(*) FROM alerts WHERE occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $reviewedAlerts = statScalar("SELECT COUNT(*) FROM alerts WHERE status <> 'open' AND occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $casesOpened = statScalar("SELECT COUNT(*) FROM cases WHERE created_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)");
    $casesResolved = statScalar("SELECT COUNT(*) FROM cases WHERE created_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) AND status IN ('resolved','closed')");

    $daily = [];
    for ($offset = 13; $offset >= 0; $offset--) {
        $date = date('Y-m-d', strtotime('-' . $offset . ' days'));
        $daily[$date] = 0;
    }
    $dailyRows = db()->query(
        "SELECT DATE(joined_at) activity_date, COUNT(DISTINCT player_uuid) total
         FROM player_sessions WHERE joined_at >= DATE_SUB(CURDATE(), INTERVAL 13 DAY)
         GROUP BY DATE(joined_at) ORDER BY activity_date"
    )->fetchAll();
    foreach ($dailyRows as $row) {
        if (isset($daily[$row['activity_date']])) $daily[$row['activity_date']] = (int) $row['total'];
    }

    $trendDays = min(90, $days);
    $trends = ['players' => [], 'sessions' => [], 'alerts' => [], 'cases' => [], 'punishments' => []];
    for ($offset = $trendDays - 1; $offset >= 0; $offset--) {
        $date = date('Y-m-d', strtotime('-' . $offset . ' days'));
        foreach ($trends as &$trend) $trend[$date] = 0;
        unset($trend);
    }
    $trendQueries = [
        'players' => "SELECT DATE(joined_at) chart_date, COUNT(DISTINCT player_uuid) total FROM player_sessions WHERE joined_at >= DATE_SUB(CURDATE(), INTERVAL " . ($trendDays - 1) . " DAY) GROUP BY DATE(joined_at)",
        'sessions' => "SELECT DATE(joined_at) chart_date, COUNT(*) total FROM player_sessions WHERE joined_at >= DATE_SUB(CURDATE(), INTERVAL " . ($trendDays - 1) . " DAY) GROUP BY DATE(joined_at)",
        'alerts' => "SELECT DATE(occurred_at) chart_date, COUNT(*) total FROM alerts WHERE occurred_at >= DATE_SUB(CURDATE(), INTERVAL " . ($trendDays - 1) . " DAY) GROUP BY DATE(occurred_at)",
        'cases' => "SELECT DATE(created_at) chart_date, COUNT(*) total FROM cases WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL " . ($trendDays - 1) . " DAY) GROUP BY DATE(created_at)",
        'punishments' => "SELECT DATE(issued_at) chart_date, COUNT(*) total FROM punishments WHERE issued_at >= DATE_SUB(CURDATE(), INTERVAL " . ($trendDays - 1) . " DAY) GROUP BY DATE(issued_at)",
    ];
    foreach ($trendQueries as $key => $sql) {
        foreach (db()->query($sql)->fetchAll() as $row) {
            if (isset($trends[$key][(string) $row['chart_date']])) $trends[$key][(string) $row['chart_date']] = (int) $row['total'];
        }
    }
    $joinHours = array_fill_keys(array_map(static fn(int $hour): string => str_pad((string) $hour, 2, '0', STR_PAD_LEFT) . ':00', range(0, 23)), 0);
    foreach (db()->query("SELECT HOUR(joined_at) chart_hour, COUNT(*) total FROM player_sessions WHERE joined_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY HOUR(joined_at) ORDER BY HOUR(joined_at)")->fetchAll() as $row) {
        $joinHours[str_pad((string) $row['chart_hour'], 2, '0', STR_PAD_LEFT) . ':00'] = (int) $row['total'];
    }

    return [
        'days' => $days,
        'overview' => [
            'players' => (int) statScalar('SELECT COUNT(*) FROM players'),
            'online' => (int) statScalar('SELECT COUNT(*) FROM players WHERE online = 1'),
            'new' => (int) statScalar("SELECT COUNT(*) FROM players WHERE first_seen_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"),
            'active_24h' => (int) statScalar('SELECT COUNT(*) FROM players WHERE last_seen_at >= DATE_SUB(NOW(), INTERVAL 1 DAY)'),
            'active_7d' => (int) statScalar('SELECT COUNT(*) FROM players WHERE last_seen_at >= DATE_SUB(NOW(), INTERVAL 7 DAY)'),
            'active_30d' => (int) statScalar('SELECT COUNT(*) FROM players WHERE last_seen_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)'),
            'active_period' => (int) $active,
            'returning' => (int) $returning,
            'returning_rate' => percent($returning, $active),
            'd1_retention' => percent($retainedD1, $eligibleD1),
            'd3_retention' => percent($retainedD3, $eligibleD3),
            'd7_retention' => percent($retainedD7, $eligibleD7),
            'd30_retention' => percent($retainedD30, $eligibleD30),
            'churn_risk' => (int) statScalar('SELECT COUNT(*) FROM players WHERE last_seen_at < DATE_SUB(NOW(), INTERVAL 7 DAY) AND last_seen_at >= DATE_SUB(NOW(), INTERVAL 30 DAY)'),
        ],
        'engagement' => [
            'sessions' => (int) $sessions,
            'sessions_per_active' => $active > 0 ? round($sessions / $active, 1) : 0.0,
            'avg_session_minutes' => round(statScalar("SELECT AVG(duration_seconds) / 60 FROM player_sessions WHERE duration_seconds IS NOT NULL AND joined_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"), 1),
            'total_playtime_hours' => round(statScalar('SELECT SUM(playtime_seconds) / 3600 FROM players'), 1),
            'avg_playtime_hours' => round(statScalar('SELECT AVG(playtime_seconds) / 3600 FROM players'), 1),
            'players_10h' => (int) statScalar('SELECT COUNT(*) FROM players WHERE playtime_seconds >= 36000'),
            'town_members' => (int) $townMembers,
            'town_adoption' => percent($townMembers, $knownPlayers),
        ],
        'moderation' => [
            'alerts' => (int) $alertsInPeriod,
            'alerts_per_active' => $active > 0 ? round($alertsInPeriod / $active, 2) : 0.0,
            'review_rate' => percent($reviewedAlerts, $alertsInPeriod),
            'unique_flagged' => (int) statScalar("SELECT COUNT(DISTINCT COALESCE(player_uuid, player_name)) FROM alerts WHERE occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"),
            'critical' => (int) statScalar("SELECT COUNT(*) FROM alerts WHERE severity = 'critical' AND occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"),
            'open_alerts' => (int) statScalar("SELECT COUNT(*) FROM alerts WHERE status IN ('open','escalated')"),
            'cases_opened' => (int) $casesOpened,
            'cases_resolved' => (int) $casesResolved,
            'case_close_rate' => percent($casesResolved, $casesOpened),
            'case_backlog' => (int) statScalar("SELECT COUNT(*) FROM cases WHERE status NOT IN ('resolved','closed')"),
            'resolution_hours' => round(statScalar("SELECT AVG(TIMESTAMPDIFF(MINUTE, created_at, resolved_at)) / 60 FROM cases WHERE resolved_at IS NOT NULL AND resolved_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"), 1),
            'punishments' => (int) statScalar("SELECT COUNT(*) FROM punishments WHERE issued_at >= DATE_SUB(NOW(), INTERVAL $interval DAY)"),
        ],
        'economy' => [
            'total_balance' => round(statScalar('SELECT SUM(balance) FROM players WHERE balance IS NOT NULL'), 2),
            'average_balance' => round(statScalar('SELECT AVG(balance) FROM players WHERE balance IS NOT NULL'), 2),
            'towns' => (int) statScalar("SELECT COUNT(DISTINCT town_name) FROM players WHERE town_name IS NOT NULL AND town_name <> ''"),
        ],
        'daily' => $daily,
        'trends' => $trends,
        'join_hours_line' => $joinHours,
        'platforms' => db()->query('SELECT platform label, COUNT(*) total FROM players GROUP BY platform ORDER BY total DESC')->fetchAll(),
        'session_servers' => db()->query("SELECT COALESCE(server_name, 'network') label, COUNT(*) total FROM player_sessions WHERE joined_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY server_name ORDER BY total DESC LIMIT 8")->fetchAll(),
        'join_hours' => array_map(static fn(string $label, int $total): array => ['label' => $label, 'total' => $total], array_keys($joinHours), array_values($joinHours)),
        'join_days' => db()->query("SELECT DAYNAME(joined_at) label, COUNT(*) total FROM player_sessions WHERE joined_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY DAYOFWEEK(joined_at), DAYNAME(joined_at) ORDER BY DAYOFWEEK(joined_at)")->fetchAll(),
        'top_towns' => db()->query("SELECT town_name label, COUNT(*) total FROM players WHERE town_name IS NOT NULL AND town_name <> '' GROUP BY town_name ORDER BY total DESC LIMIT 10")->fetchAll(),
        'playtime_bands' => db()->query("SELECT 'Under 1 hour' label, COUNT(*) total FROM players WHERE playtime_seconds < 3600 UNION ALL SELECT '1 to 10 hours', COUNT(*) FROM players WHERE playtime_seconds >= 3600 AND playtime_seconds < 36000 UNION ALL SELECT '10 to 50 hours', COUNT(*) FROM players WHERE playtime_seconds >= 36000 AND playtime_seconds < 180000 UNION ALL SELECT '50 to 100 hours', COUNT(*) FROM players WHERE playtime_seconds >= 180000 AND playtime_seconds < 360000 UNION ALL SELECT '100+ hours', COUNT(*) FROM players WHERE playtime_seconds >= 360000")->fetchAll(),
        'wealth_bands' => db()->query("SELECT 'Under 10k' label, COUNT(*) total FROM players WHERE balance IS NOT NULL AND balance < 10000 UNION ALL SELECT '10k to 50k', COUNT(*) FROM players WHERE balance >= 10000 AND balance < 50000 UNION ALL SELECT '50k to 100k', COUNT(*) FROM players WHERE balance >= 50000 AND balance < 100000 UNION ALL SELECT '100k to 500k', COUNT(*) FROM players WHERE balance >= 100000 AND balance < 500000 UNION ALL SELECT '500k+', COUNT(*) FROM players WHERE balance >= 500000")->fetchAll(),
        'top_checks' => db()->query("SELECT check_name label, COUNT(*) total FROM alerts WHERE occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY check_name ORDER BY total DESC LIMIT 8")->fetchAll(),
        'severity' => db()->query("SELECT severity label, COUNT(*) total FROM alerts WHERE occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY severity ORDER BY total DESC")->fetchAll(),
        'alert_status' => db()->query("SELECT status label, COUNT(*) total FROM alerts WHERE occurred_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY status ORDER BY total DESC")->fetchAll(),
        'case_status' => db()->query("SELECT status label, COUNT(*) total FROM cases GROUP BY status ORDER BY total DESC")->fetchAll(),
        'case_priority' => db()->query("SELECT priority label, COUNT(*) total FROM cases GROUP BY priority ORDER BY total DESC")->fetchAll(),
        'punishment_types' => db()->query("SELECT punishment_type label, COUNT(*) total FROM punishments WHERE issued_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY punishment_type ORDER BY total DESC")->fetchAll(),
        'sources' => db()->query("SELECT COALESCE(source_server, 'network') label, COUNT(*) total FROM ingest_events WHERE received_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY source_server ORDER BY total DESC LIMIT 8")->fetchAll(),
        'risky_players' => db()->query('SELECT uuid, username, risk_score, last_seen_at FROM players ORDER BY risk_score DESC, last_seen_at DESC LIMIT 8')->fetchAll(),
        'staff_activity' => db()->query("SELECT u.display_name label, COUNT(*) total FROM audit_logs a JOIN users u ON u.id = a.actor_user_id AND u.active=1 WHERE a.created_at >= DATE_SUB(NOW(), INTERVAL $interval DAY) GROUP BY u.id,u.display_name ORDER BY total DESC LIMIT 8")->fetchAll(),
        'servers' => db()->query('SELECT * FROM server_status ORDER BY server_name')->fetchAll(),
        'plugins' => db()->query('SELECT * FROM server_plugins ORDER BY server_name, plugin_name')->fetchAll(),
    ];
}

/** @param array<string,int|float> $values */
function renderLineChart(array $values,string $color='#ff4de1',string $empty='No time-series data collected yet'): void
{
    if($values===[]){echo '<div class="empty-state compact"><strong>'.e($empty).'</strong><p>New collected activity will build this graph.</p></div>';return;}
    $numbers=array_values(array_map('floatval',$values));$labels=array_keys($values);$maximum=max(1,max($numbers));$minimum=min(0,min($numbers));$span=$maximum-$minimum;$count=count($numbers);$points=[];
    foreach($numbers as $i=>$n)$points[]=[round(52+($count<2?340:($i/($count-1))*692),2),round(192-(($n-$minimum)/$span)*170,2),$n,(string)$labels[$i]];
    echo '<div class="line-chart" data-interactive-chart data-chart-points="'.e(jsonEncode($points)).'"><svg viewBox="0 0 760 230" tabindex="0" role="img" aria-label="Time series in UTC. Use left and right arrow keys to inspect values.">';
    for($i=0;$i<=4;$i++){$y=22+$i/4*170;echo '<line class="chart-grid-line" x1="52" y1="'.$y.'" x2="744" y2="'.$y.'"></line><text class="chart-axis-label" x="46" y="'.($y+4).'" text-anchor="end">'.e(obsNumber($maximum-$i/4*$span)).'</text>';}
    echo '<polyline class="chart-line" style="stroke:'.e($color).'" points="'.e(implode(' ',array_map(fn($p)=>$p[0].','.$p[1],$points))).'"></polyline>';
    foreach($points as $i=>$p){echo '<circle class="chart-point" cx="'.$p[0].'" cy="'.$p[1].'" r="3" style="fill:'.e($color).'"><title>'.e($p[3].': '.obsNumber($p[2])).'</title></circle>';if($i%max(1,(int)ceil($count/7))===0||$i===$count-1){$label=preg_match('/^\d{4}-\d{2}-\d{2}/',$p[3])?gmdate(strlen($p[3])<=10?'M j':'H:i',strtotime($p[3].' UTC')):$p[3];echo '<text class="chart-axis-label" x="'.$p[0].'" y="220" text-anchor="middle">'.e($label).'</text>';}}
    echo '<line data-chart-cursor x1="0" x2="0" y1="18" y2="196" hidden></line><circle data-chart-marker r="6" cx="0" cy="0" hidden></circle></svg><output class="chart-value" aria-live="polite">Hover or use the arrow keys for exact values.</output></div>';
}

/** @param list<array<string,mixed>> $rows */
function renderBarList(array $rows, string $empty = 'No data collected yet'): void
{
    if ($rows === []) {
        echo '<div class="empty-state compact"><strong>' . e($empty) . '</strong></div>';
        return;
    }
    $max = max(array_map(static fn(array $row): int => max(0, (int) ($row['total'] ?? 0)), $rows)) ?: 1;
    echo '<div class="bar-list">';
    foreach ($rows as $row) {
        $total = max(0, (int) ($row['total'] ?? 0));
        $width = max(3, round(($total / $max) * 100, 1));
        echo '<div class="bar-row"><div><span>' . e(ucwords(str_replace('_', ' ', (string) ($row['label'] ?? 'Unknown'))))
            . '</span><strong>' . number_format($total) . '</strong></div><span class="bar-track"><i style="width:' . $width . '%"></i></span></div>';
    }
    echo '</div>';
}
