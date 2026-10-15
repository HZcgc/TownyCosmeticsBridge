<?php
declare(strict_types=1);

require_once __DIR__ . '/app/bootstrap.php';
requireAuth();

$id = (int) ($_GET['id'] ?? 0);
$statement = db()->prepare('SELECT * FROM attachments WHERE id = ? LIMIT 1');
$statement->execute([$id]);
$attachment = $statement->fetch();
if (!$attachment) {
    http_response_code(404);
    exit('File not found.');
}

$permission = match((string)$attachment['owner_type']) { 'document'=>'documents.view', 'player'=>'profile.files.view', 'case'=>'cases.view', default=>'__invalid' };
if ($attachment['owner_type']==='player' && !can('players.view')) { http_response_code(403);exit('Access denied.'); }
if (!can($permission)) {
    http_response_code(403);
    exit('Access denied.');
}
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();

$uploadRoot = realpath(APP_ROOT . '/storage/uploads');
$path = realpath(APP_ROOT . '/' . ltrim((string) $attachment['stored_path'], '/'));
if ($uploadRoot === false || $path === false || !str_starts_with($path, $uploadRoot . DIRECTORY_SEPARATOR) || !is_file($path)) {
    http_response_code(404);
    exit('File not found.');
}

$size = filesize($path);
$mime = (string) $attachment['mime_type'];
$inlineAllowed = str_starts_with($mime, 'image/') || str_starts_with($mime, 'video/')
    || str_starts_with($mime, 'audio/') || $mime === 'application/pdf' || $mime === 'text/plain';
$inline = isset($_GET['inline']) && $inlineAllowed;
if ($inline && $mime === 'application/pdf' && $attachment['owner_type'] === 'document') {
    header('X-Frame-Options: SAMEORIGIN');
    header("Content-Security-Policy: default-src 'none'; frame-ancestors 'self'");
}

$filename = preg_replace('/[^A-Za-z0-9._ -]/u', '_', (string) $attachment['original_name']) ?: 'attachment';

header('Content-Type: ' . $mime);
header('Accept-Ranges: bytes');
header('Cache-Control: private, max-age=300');
header('Content-Disposition: ' . ($inline ? 'inline' : 'attachment') . '; filename="' . addcslashes($filename, '"\\') . '"');
header('X-Content-Type-Options: nosniff');

$start = 0;
$end = $size - 1;
if (isset($_SERVER['HTTP_RANGE']) && preg_match('/^bytes=(\d*)-(\d*)$/', (string) $_SERVER['HTTP_RANGE'], $matches)) {
    if ($matches[1] !== '') $start = max(0, (int) $matches[1]);
    if ($matches[2] !== '') $end = min($end, (int) $matches[2]);
    if ($start > $end || $start >= $size) {
        http_response_code(416);
        header('Content-Range: bytes */' . $size);
        exit;
    }
    http_response_code(206);
    header('Content-Range: bytes ' . $start . '-' . $end . '/' . $size);
}

$length = $end - $start + 1;
header('Content-Length: ' . $length);
$handle = fopen($path, 'rb');
if ($handle === false) {
    http_response_code(500);
    exit;
}
fseek($handle, $start);
while (!feof($handle) && $length > 0) {
    $chunk = fread($handle, min(1_048_576, $length));
    if ($chunk === false || $chunk === '') break;
    echo $chunk;
    $length -= strlen($chunk);
    if (connection_aborted()) break;
}
fclose($handle);
