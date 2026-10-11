<?php
declare(strict_types=1);

require_once __DIR__ . '/app/bootstrap.php';

header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store');

try {
    if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
        uploadResponse(405, ['ok' => false, 'error' => 'Method not allowed.']);
    }
    $user = currentUser();
    if (!$user) uploadResponse(401, ['ok' => false, 'error' => 'Your session expired. Sign in again.']);
    $csrf = (string) ($_POST['csrf_token'] ?? $_SERVER['HTTP_X_CSRF_TOKEN'] ?? '');
    if ($csrf === '' || !hash_equals(csrfToken(), $csrf)) {
        uploadResponse(419, ['ok' => false, 'error' => 'Your session token expired. Refresh the page and try again.']);
    }

    $action = (string) ($_POST['action'] ?? '');
    if ($action === 'init') {
        uploadInit($user);
    } elseif ($action === 'chunk') {
        uploadChunk($user);
    } elseif ($action === 'complete') {
        uploadComplete($user);
    } elseif ($action === 'abort') {
        uploadAbort($user);
    } else {
        uploadResponse(422, ['ok' => false, 'error' => 'Unknown upload action.']);
    }
} catch (Throwable $exception) {
    uploadResponse(422, ['ok' => false, 'error' => $exception->getMessage()]);
}

function uploadInit(array $user): never
{
    $ownerType = (string) ($_POST['owner_type'] ?? '');
    $ownerId = (int) ($_POST['owner_id'] ?? 0);
    assertUploadOwner($ownerType, $ownerId, $user);

    $name = mb_substr(basename(trim((string) ($_POST['name'] ?? 'attachment'))), 0, 255);
    $size = filter_var($_POST['size'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
    $chunkCount = filter_var($_POST['chunks'] ?? null, FILTER_VALIDATE_INT, ['options' => ['min_range' => 1, 'max_range' => 200000]]);
    if ($size === false || $chunkCount === false || $name === '') {
        throw new RuntimeException('Invalid file information.');
    }
    if ($ownerType==='player' && (int)$size>104857600) throw new RuntimeException('Profile attachments are limited to 100 MB per file.');
    $free = @disk_free_space(APP_ROOT . '/storage');
    if ($free !== false && $free < (int) $size + 10_485_760) {
        throw new RuntimeException('There is not enough free evidence storage for this file.');
    }

    cleanupStaleChunks((int) $user['id']);
    $uploadId = bin2hex(random_bytes(24));
    $directory = chunkDirectory((int) $user['id'], $uploadId);
    if (!mkdir($directory, 0750, true) && !is_dir($directory)) {
        throw new RuntimeException('The temporary upload directory could not be created.');
    }
    $meta = [
        'user_id' => (int) $user['id'], 'owner_type' => $ownerType, 'owner_id' => $ownerId,
        'name' => $name, 'size' => (int) $size, 'chunks' => (int) $chunkCount,
        'created_at' => time(),
    ];
    if (file_put_contents($directory . '/meta.json', jsonEncode($meta), LOCK_EX) === false) {
        removeChunkDirectory($directory);
        throw new RuntimeException('The upload could not be initialized.');
    }
    uploadResponse(200, ['ok' => true, 'upload_id' => $uploadId, 'chunk_size' => 524288]);
}

function uploadChunk(array $user): never
{
    [$directory, $meta] = loadChunkUpload($user);
    $index = filter_var($_POST['index'] ?? null, FILTER_VALIDATE_INT, [
        'options' => ['min_range' => 0, 'max_range' => max(0, (int) $meta['chunks'] - 1)],
    ]);
    if ($index === false || !isset($_FILES['chunk']) || !is_array($_FILES['chunk'])) {
        throw new RuntimeException('Invalid upload chunk.');
    }
    $file = $_FILES['chunk'];
    $error = (int) ($file['error'] ?? UPLOAD_ERR_NO_FILE);
    if ($error !== UPLOAD_ERR_OK) throw new RuntimeException(uploadErrorMessage($error));
    $size = (int) ($file['size'] ?? 0);
    if ($size < 1 || $size > 786432) throw new RuntimeException('The upload chunk is outside the permitted transport size.');
    $temporary = (string) ($file['tmp_name'] ?? '');
    if (!is_uploaded_file($temporary)) throw new RuntimeException('The upload chunk could not be verified.');
    $destination = $directory . '/' . str_pad((string) $index, 8, '0', STR_PAD_LEFT) . '.part';
    if (!move_uploaded_file($temporary, $destination)) throw new RuntimeException('The upload chunk could not be stored.');
    @chmod($destination, 0640);
    uploadResponse(200, ['ok' => true, 'index' => (int) $index]);
}

function uploadComplete(array $user): never
{
    [$directory, $meta] = loadChunkUpload($user);
    assertUploadOwner((string) $meta['owner_type'], (int) $meta['owner_id'], $user);

    $relativeDirectory = 'storage/uploads/' . date('Y/m');
    $absoluteDirectory = APP_ROOT . '/' . $relativeDirectory;
    if (!is_dir($absoluteDirectory) && !mkdir($absoluteDirectory, 0750, true) && !is_dir($absoluteDirectory)) {
        throw new RuntimeException('The evidence directory could not be created.');
    }
    $assembled = $absoluteDirectory . '/' . bin2hex(random_bytes(24)) . '.assembling';
    $output = fopen($assembled, 'wb');
    if ($output === false) throw new RuntimeException('The uploaded file could not be assembled.');
    $written = 0;
    try {
        for ($index = 0; $index < (int) $meta['chunks']; $index++) {
            $part = $directory . '/' . str_pad((string) $index, 8, '0', STR_PAD_LEFT) . '.part';
            if (!is_file($part)) throw new RuntimeException('One upload chunk is missing. Retry the upload.');
            $input = fopen($part, 'rb');
            if ($input === false) throw new RuntimeException('An upload chunk could not be read.');
            $copied = stream_copy_to_stream($input, $output);
            fclose($input);
            if ($copied === false) throw new RuntimeException('An upload chunk could not be assembled.');
            $written += $copied;
        }
    } finally {
        fclose($output);
    }
    if ($written !== (int) $meta['size']) {
        @unlink($assembled);
        throw new RuntimeException('The completed file size does not match the upload.');
    }

    $mime = (string) (new finfo(FILEINFO_MIME_TYPE))->file($assembled);
    $allowed = allowedAttachmentTypes();
    if (!isset($allowed[$mime])) {
        @unlink($assembled);
        throw new RuntimeException('Unsupported file type: ' . $mime . '.');
    }
    $storedName = bin2hex(random_bytes(24)) . '.' . $allowed[$mime];
    $relativePath = $relativeDirectory . '/' . $storedName;
    $absolutePath = APP_ROOT . '/' . $relativePath;
    if (!rename($assembled, $absolutePath)) {
        @unlink($assembled);
        throw new RuntimeException('The completed attachment could not be stored.');
    }
    @chmod($absolutePath, 0640);

    try {
        $statement = db()->prepare(
            'INSERT INTO attachments (owner_type, owner_id, original_name, stored_path, mime_type, size_bytes, sha256, uploaded_by, created_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $statement->execute([
            $meta['owner_type'], (int) $meta['owner_id'], $meta['name'], $relativePath, $mime,
            $written, hash_file('sha256', $absolutePath), (int) $user['id'], now(),
        ]);
        $attachmentId = (int) db()->lastInsertId();
        audit($meta['owner_type'] . '.attachment_uploaded', (string) $meta['owner_type'], (string) $meta['owner_id'], [
            'attachment_id' => $attachmentId, 'name' => $meta['name'], 'size' => $written, 'chunked' => true,
        ]);
    } catch (Throwable $exception) {
        @unlink($absolutePath);
        throw $exception;
    }
    removeChunkDirectory($directory);
    uploadResponse(200, ['ok' => true, 'attachment_id' => $attachmentId, 'name' => $meta['name']]);
}

function uploadAbort(array $user): never
{
    [$directory] = loadChunkUpload($user);
    removeChunkDirectory($directory);
    uploadResponse(200, ['ok' => true]);
}

function assertUploadOwner(string $ownerType, int $ownerId, array $user): void
{
    if (!in_array($ownerType, ['case', 'document', 'player'], true) || $ownerId <= 0) {
        throw new RuntimeException('Invalid upload destination.');
    }
    if ($ownerType==='player') {
        if (!can('players.view',$user) || !can('profile.files.create',$user)) throw new RuntimeException('Your staff role cannot upload profile files.');
        dossierPlayer($ownerId); return;
    }
    $allowed = $ownerType === 'document'
        ? can('documents.manage', $user)
        : (can('cases.manage', $user) || can('cases.create', $user));
    if (!$allowed) throw new RuntimeException('Your staff role cannot upload files here.');
    $table = $ownerType === 'document' ? 'documents' : 'cases';
    $statement = db()->prepare("SELECT COUNT(*) FROM `$table` WHERE id = ?");
    $statement->execute([$ownerId]);
    if ((int) $statement->fetchColumn() === 0) throw new RuntimeException('The upload destination no longer exists.');
}

/** @return array{0:string,1:array<string,mixed>} */
function loadChunkUpload(array $user): array
{
    $uploadId = (string) ($_POST['upload_id'] ?? '');
    if (!preg_match('/^[a-f0-9]{48}$/', $uploadId)) throw new RuntimeException('Invalid upload identifier.');
    $directory = chunkDirectory((int) $user['id'], $uploadId);
    $raw = @file_get_contents($directory . '/meta.json');
    $meta = is_string($raw) ? json_decode($raw, true) : null;
    if (!is_array($meta) || (int) ($meta['user_id'] ?? 0) !== (int) $user['id']) {
        throw new RuntimeException('This upload expired or does not belong to your session.');
    }
    if (time() - (int) ($meta['created_at'] ?? 0) > 21600) {
        removeChunkDirectory($directory);
        throw new RuntimeException('This upload expired. Start it again.');
    }
    return [$directory, $meta];
}

function chunkDirectory(int $userId, string $uploadId): string
{
    return APP_ROOT . '/storage/chunks/' . $userId . '/' . $uploadId;
}

function cleanupStaleChunks(int $userId): void
{
    $root = APP_ROOT . '/storage/chunks/' . $userId;
    if (!is_dir($root)) return;
    foreach (glob($root . '/*', GLOB_ONLYDIR) ?: [] as $directory) {
        $meta = @json_decode((string) @file_get_contents($directory . '/meta.json'), true);
        if (!is_array($meta) || time() - (int) ($meta['created_at'] ?? 0) > 21600) removeChunkDirectory($directory);
    }
}

function removeChunkDirectory(string $directory): void
{
    $root = realpath(APP_ROOT . '/storage/chunks');
    $resolved = realpath($directory);
    if ($root === false || $resolved === false || !str_starts_with($resolved, $root . DIRECTORY_SEPARATOR)) return;
    foreach (glob($resolved . '/*') ?: [] as $file) if (is_file($file)) @unlink($file);
    @rmdir($resolved);
}

function uploadResponse(int $status, array $body): never
{
    http_response_code($status);
    echo jsonEncode($body);
    exit;
}
