<?php
declare(strict_types=1);

require_once __DIR__ . '/app/bootstrap.php';
requireAuth();
if (session_status() === PHP_SESSION_ACTIVE) session_write_close();

$uuid = normalizeUuid((string) ($_GET['uuid'] ?? ''));
$name = mb_substr(trim((string) ($_GET['name'] ?? 'Player')), 0, 32);
$size = max(24, min(128, (int) ($_GET['size'] ?? 48)));
$identity = $uuid !== null ? str_replace('-', '', $uuid) : ($name !== '' ? $name : 'MHF_Steve');

header('Cache-Control: private, max-age=3600');
header('X-Content-Type-Options: nosniff');

$cacheDirectory = APP_ROOT . '/storage/avatars';
if (!is_dir($cacheDirectory)) @mkdir($cacheDirectory, 0750, true);
$cacheKey = hash('sha256', strtolower(($uuid ?? '') . '|' . $name));
$cacheFile = $cacheDirectory . '/' . $cacheKey . '-' . $size . '.png';
if (is_file($cacheFile) && filemtime($cacheFile) > time() - 604_800) {
    header('Content-Type: image/png');
    header('Content-Length: ' . filesize($cacheFile));
    readfile($cacheFile);
    exit;
}

$providers = [];
if ($uuid !== null) {
    $compactUuid = str_replace('-', '', $uuid);
    $providers[] = 'https://mc-heads.net/avatar/' . rawurlencode($compactUuid) . '/' . $size;
    $providers[] = 'https://crafatar.com/avatars/' . rawurlencode($compactUuid)
        . '?size=' . $size . '&overlay&default=MHF_Steve';
}
if ($name !== '') {
    $providers[] = 'https://mc-heads.net/avatar/' . rawurlencode($name) . '/' . $size;
    $providers[] = 'https://minotar.net/helm/' . rawurlencode($name) . '/' . $size . '.png';
}

foreach (array_values(array_unique($providers)) as $remote) {
    $image = fetchAvatarImage($remote);
    if ($image === null) continue;
    @file_put_contents($cacheFile, $image, LOCK_EX);
    @chmod($cacheFile, 0640);
    header('Content-Type: image/png');
    header('Content-Length: ' . strlen($image));
    echo $image;
    exit;
}

// Some web hosts block outgoing PHP requests. In that situation, let the
// signed-in staff member's browser request the avatar directly instead of
// replacing every player with a letter tile.
header('Location: https://mc-heads.net/avatar/' . rawurlencode($identity) . '/' . $size, true, 302);
exit;

function fetchAvatarImage(string $url): ?string
{
    $response = null;
    $status = 0;
    $contentType = '';

    if (function_exists('curl_init')) {
        $curl = curl_init($url);
        curl_setopt_array($curl, [
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_FOLLOWLOCATION => true,
            CURLOPT_MAXREDIRS => 3,
            CURLOPT_CONNECTTIMEOUT => 4,
            CURLOPT_TIMEOUT => 10,
            CURLOPT_USERAGENT => 'TownyRebornStaffPortal/2.1',
            CURLOPT_PROTOCOLS => CURLPROTO_HTTPS,
            CURLOPT_REDIR_PROTOCOLS => CURLPROTO_HTTPS,
            CURLOPT_MAXFILESIZE => 2_097_152,
        ]);
        $result = curl_exec($curl);
        $status = (int) curl_getinfo($curl, CURLINFO_RESPONSE_CODE);
        $contentType = strtolower((string) curl_getinfo($curl, CURLINFO_CONTENT_TYPE));
        curl_close($curl);
        if (is_string($result)) $response = $result;
    } elseif ((bool) ini_get('allow_url_fopen')) {
        $context = stream_context_create([
            'http' => [
                'method' => 'GET',
                'timeout' => 10,
                'follow_location' => 1,
                'max_redirects' => 3,
                'header' => "User-Agent: TownyRebornStaffPortal/2.1\r\nAccept: image/png,image/*\r\n",
            ],
            'ssl' => ['verify_peer' => true, 'verify_peer_name' => true],
        ]);
        $result = @file_get_contents($url, false, $context, 0, 2_097_153);
        if (is_string($result)) {
            $response = $result;
            foreach ($http_response_header ?? [] as $header) {
                if (preg_match('#^HTTP/\S+\s+(\d+)#i', $header, $match)) $status = (int) $match[1];
                if (stripos($header, 'Content-Type:') === 0) $contentType = strtolower(trim(substr($header, 13)));
            }
        }
    }

    if (!is_string($response) || $status !== 200 || strlen($response) < 67 || strlen($response) > 2_097_152) {
        return null;
    }
    if ($contentType !== '' && !str_starts_with($contentType, 'image/')) return null;
    // All supported services return PNG. Checking the signature prevents an
    // HTML error page from ever being served as a trusted avatar image.
    if (!str_starts_with($response, "\x89PNG\r\n\x1a\n")) return null;
    return $response;
}
