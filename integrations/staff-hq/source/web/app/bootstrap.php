<?php
declare(strict_types=1);

define('APP_ROOT', dirname(__DIR__));

$configPath = APP_ROOT . '/config/config.php';
if (!is_file($configPath)) {
    if (basename($_SERVER['SCRIPT_NAME'] ?? '') !== 'install.php') {
        header('Location: install.php');
        exit;
    }
    return;
}

/** @var array<string,mixed> $GLOBALS['app_config'] */
$GLOBALS['app_config'] = require $configPath;

require_once APP_ROOT . '/app/Totp.php';

date_default_timezone_set((string) config('timezone', 'Europe/Berlin'));

header('X-Content-Type-Options: nosniff');
header('X-Frame-Options: DENY');
header('Referrer-Policy: no-referrer');
header('Permissions-Policy: camera=(), microphone=(), geolocation=(), payment=(), usb=()');
header("Content-Security-Policy: default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data: blob: https://mc-heads.net https://crafatar.com https://minotar.net https://textures.minecraft.net; font-src 'self'; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'; object-src 'none'");
if (isHttps()) {
    header('Strict-Transport-Security: max-age=31536000');
}

// Machine-to-machine API calls never need a browser session. Avoiding a new
// PHP session for every connector request saves a PHP worker, disk I/O and a
// Set-Cookie response on small shared-hosting plans.
$isApiRequest = PHP_SAPI === 'cli' || in_array(basename((string)($_SERVER['SCRIPT_NAME']??'')),['api.php','notification-cron.php'],true);
if (!$isApiRequest) {
    ini_set('session.use_strict_mode', '1');
    ini_set('session.use_only_cookies', '1');
    // The application enforces a one-hour idle limit and a 24-hour maximum.
    // PHP's default 24-minute garbage collection must not shorten that limit.
    ini_set('session.gc_maxlifetime', '86400');
    session_name('tr_staff_session');
    session_set_cookie_params([
        'lifetime' => 0,
        'path' => '/',
        'secure' => isHttps(),
        'httponly' => true,
        'samesite' => 'Strict',
    ]);

    if (session_status() !== PHP_SESSION_ACTIVE) {
        session_start();
    }

    enforceSessionLifetime();
}

function config(?string $path = null, mixed $default = null): mixed
{
    $value = $GLOBALS['app_config'] ?? [];
    if ($path === null) {
        return $value;
    }

    foreach (explode('.', $path) as $segment) {
        if (!is_array($value) || !array_key_exists($segment, $value)) {
            return $default;
        }
        $value = $value[$segment];
    }

    return $value;
}

function isHttps(): bool
{
    if (($_SERVER['HTTPS'] ?? '') !== '' && ($_SERVER['HTTPS'] ?? '') !== 'off') {
        return true;
    }
    return strtolower((string) ($_SERVER['HTTP_X_FORWARDED_PROTO'] ?? '')) === 'https';
}

function db(): PDO
{
    static $pdo = null;
    if ($pdo instanceof PDO) {
        return $pdo;
    }

    $db = (array) config('db', []);
    $dsn = sprintf(
        'mysql:host=%s;port=%d;dbname=%s;charset=utf8mb4',
        (string) ($db['host'] ?? ''),
        (int) ($db['port'] ?? 3306),
        (string) ($db['database'] ?? '')
    );

    $options = [
        PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION,
        PDO::ATTR_DEFAULT_FETCH_MODE => PDO::FETCH_ASSOC,
        PDO::ATTR_EMULATE_PREPARES => false,
        PDO::ATTR_TIMEOUT => 8,
    ];

    if (!empty($db['ssl']) && defined('PDO::MYSQL_ATTR_SSL_CA')) {
        $ca = (string) ($db['ssl_ca'] ?? '');
        if ($ca !== '') {
            $options[PDO::MYSQL_ATTR_SSL_CA] = $ca;
        }
        if (defined('PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT')) {
            $options[PDO::MYSQL_ATTR_SSL_VERIFY_SERVER_CERT] = true;
        }
    }

    $pdo = new PDO(
        $dsn,
        (string) ($db['username'] ?? ''),
        (string) ($db['password'] ?? ''),
        $options
    );

    $pdo->exec("SET time_zone = '" . date('P') . "'");
    return $pdo;
}

function now(): string
{
    return date('Y-m-d H:i:s');
}

function e(mixed $value): string
{
    return htmlspecialchars((string) $value, ENT_QUOTES | ENT_SUBSTITUTE, 'UTF-8');
}

function jsonEncode(mixed $value): string
{
    return json_encode($value, JSON_UNESCAPED_SLASHES | JSON_UNESCAPED_UNICODE | JSON_THROW_ON_ERROR);
}

function appKey(): string
{
    $key = (string) config('app_key', '');
    if (str_starts_with($key, 'base64:')) {
        $decoded = base64_decode(substr($key, 7), true);
        if ($decoded !== false && strlen($decoded) >= 32) {
            return substr($decoded, 0, 32);
        }
    }
    return hash('sha256', $key, true);
}

function encryptValue(string $plaintext): string
{
    $iv = random_bytes(12);
    $tag = '';
    $ciphertext = openssl_encrypt($plaintext, 'aes-256-gcm', appKey(), OPENSSL_RAW_DATA, $iv, $tag);
    if ($ciphertext === false) {
        throw new RuntimeException('Encryption failed.');
    }
    return base64_encode($iv . $tag . $ciphertext);
}

function decryptValue(string $encoded): string
{
    $raw = base64_decode($encoded, true);
    if ($raw === false || strlen($raw) < 29) {
        throw new RuntimeException('Invalid encrypted value.');
    }
    $iv = substr($raw, 0, 12);
    $tag = substr($raw, 12, 16);
    $ciphertext = substr($raw, 28);
    $plaintext = openssl_decrypt($ciphertext, 'aes-256-gcm', appKey(), OPENSSL_RAW_DATA, $iv, $tag);
    if ($plaintext === false) {
        throw new RuntimeException('Decryption failed.');
    }
    return $plaintext;
}

function keyedHash(string $value): string
{
    return hash_hmac('sha256', strtolower(trim($value)), appKey());
}

function clientIp(): string
{
    return (string) ($_SERVER['REMOTE_ADDR'] ?? 'unknown');
}

function csrfToken(): string
{
    if (empty($_SESSION['csrf_token'])) {
        $_SESSION['csrf_token'] = bin2hex(random_bytes(32));
    }
    return (string) $_SESSION['csrf_token'];
}

function csrfField(): string
{
    return '<input type="hidden" name="csrf_token" value="' . e(csrfToken()) . '">';
}

function verifyCsrf(): void
{
    $submitted = (string) ($_POST['csrf_token'] ?? '');
    if ($submitted === '' || !hash_equals(csrfToken(), $submitted)) {
        http_response_code(419);
        exit('Your session token expired. Go back, refresh the page and try again.');
    }
}

function redirect(string $page, array $params = []): never
{
    $params = array_merge(['page' => $page], $params);
    header('Location: index.php?' . http_build_query($params));
    exit;
}

function setFlash(string $type, string $message): void
{
    $_SESSION['flash'][] = ['type' => $type, 'message' => $message];
}

function takeFlashes(): array
{
    $flashes = $_SESSION['flash'] ?? [];
    unset($_SESSION['flash']);
    return is_array($flashes) ? $flashes : [];
}

function enforceSessionLifetime(): void
{
    $now = time();
    if (isset($_SESSION['last_activity']) && $now - (int) $_SESSION['last_activity'] > 3_600) {
        $_SESSION = [];
        session_regenerate_id(true);
        $_SESSION['timed_out'] = true;
    }
    if (isset($_SESSION['started_at']) && $now - (int) $_SESSION['started_at'] > 86_400) {
        $_SESSION = [];
        session_regenerate_id(true);
    }
    $background = ($_SERVER['HTTP_X_TR_BACKGROUND'] ?? '') === '1' && ($_GET['page'] ?? '') === 'plugins';
    if (!$background) $_SESSION['last_activity'] = $now;
    $_SESSION['started_at'] ??= $now;
}

function currentUser(): ?array
{
    static $loadedId = null;
    static $cached = null;

    $id = isset($_SESSION['user_id']) ? (int) $_SESSION['user_id'] : 0;
    if ($id <= 0) {
        return null;
    }
    if ($loadedId === $id) {
        return $cached;
    }

    $statement = db()->prepare('SELECT id, email, display_name, minecraft_uuid, minecraft_username, role, active, must_change_password, totp_enabled, session_version, last_login_at FROM users WHERE id = ? LIMIT 1');
    $statement->execute([$id]);
    $user = $statement->fetch();
    if (!$user || !(bool) $user['active'] || (int) ($_SESSION['session_version'] ?? 0) !== (int) $user['session_version']) {
        unset($_SESSION['user_id']);
        return null;
    }

    $loadedId = $id;
    $cached = $user;
    return $user;
}

function preauthUser(): ?array
{
    if (!isset($_SESSION['preauth_started_at']) || time() - (int) $_SESSION['preauth_started_at'] > 600) {
        unset($_SESSION['preauth_user_id'], $_SESSION['preauth_started_at'], $_SESSION['pending_totp_secret']);
        return null;
    }
    $id = isset($_SESSION['preauth_user_id']) ? (int) $_SESSION['preauth_user_id'] : 0;
    if ($id <= 0) {
        return null;
    }
    $statement = db()->prepare('SELECT * FROM users WHERE id = ? AND active = 1 LIMIT 1');
    $statement->execute([$id]);
    return $statement->fetch() ?: null;
}

function requireAuth(): array
{
    $user = currentUser();
    if (!$user) {
        redirect('login');
    }
    return $user;
}

function permissionsForRole(string $role): array
{
    if ($role === 'owner') return ['*'];
    if (!in_array($role, ['admin', 'moderator', 'helper'], true)) $role = 'helper';

    static $cache = [];
    if (array_key_exists($role, $cache)) return $cache[$role];
    try {
        $statement = db()->prepare('SELECT permission, allowed FROM role_permissions WHERE role = ?');
        $statement->execute([$role]);
        $rows = $statement->fetchAll();
        if ($rows !== []) {
            return $cache[$role] = array_values(array_map(
                static fn(array $row): string => (string) $row['permission'],
                array_filter($rows, static fn(array $row): bool => (bool) $row['allowed'])
            ));
        }
    } catch (Throwable) {
        // The installer may ask for permissions before the upgrade table exists.
    }
    return $cache[$role] = defaultRolePermissions($role);
}

/** @return array<string,array{group:string,label:string}> */
function permissionCatalog(): array
{
    return [
        'dashboard.view' => ['group' => 'Overview', 'label' => 'View dashboard'],
        'analytics.view' => ['group' => 'Overview', 'label' => 'View analytics and server health'],
        'plugins.view' => ['group' => 'Server', 'label' => 'View plugins, release checks and diagnostics'],
        'activity.view' => ['group' => 'Activity logs', 'label' => 'View gameplay activity for individual players'],
        'messages.review' => ['group' => 'Activity logs', 'label' => 'Review chat hints and save incident cases'],
        'players.view' => ['group' => 'Players', 'label' => 'View player profiles and history'],
        'alerts.view' => ['group' => 'Moderation', 'label' => 'View anti-cheat alerts'],
        'alerts.manage' => ['group' => 'Moderation', 'label' => 'Review and dismiss alerts'],
        'cases.view' => ['group' => 'Cases', 'label' => 'View cases and evidence'],
        'cases.create' => ['group' => 'Cases', 'label' => 'Create cases and internal notes'],
        'cases.manage' => ['group' => 'Cases', 'label' => 'Manage cases, subjects and evidence'],
        'punishments.view' => ['group' => 'Punishments', 'label' => 'View punishment history'],
        'punishments.create' => ['group' => 'Punishments', 'label' => 'Record punishment decisions'],
        'punishments.execute' => ['group' => 'Punishments', 'label' => 'Execute punishments on the SMP'],
        'documents.view' => ['group' => 'Documentation', 'label' => 'Read staff documentation'],
        'documents.manage' => ['group' => 'Documentation', 'label' => 'Create, import, edit and delete documents'],
        'team.view' => ['group' => 'Administration', 'label' => 'View staff accounts'],
        'team.manage' => ['group' => 'Administration', 'label' => 'Manage staff accounts'],
        'commands.view' => ['group' => 'Activity logs', 'label' => 'View player command logs'],
        'messages.view' => ['group' => 'Activity logs', 'label' => 'View public and private message logs'],
        'audit.view' => ['group' => 'Administration', 'label' => 'View staff and website audit trail'],
        'api.manage' => ['group' => 'Administration', 'label' => 'Manage connector tokens and system data'],
    ] + workspacePermissionCatalog();
}

function defaultRolePermissions(string $role): array
{
    $helper = [
        'dashboard.view','analytics.view','plugins.view','activity.view','players.view',
        'alerts.view','cases.view','cases.create','cases.manage','punishments.view',
        'documents.view','team.view','messages.view',
        'profile.notes.view','profile.notes.create','profile.notes.manage',
        'profile.pins.view','profile.pins.create','profile.files.view','profile.files.create',
        'statistics.crates','statistics.economy','statistics.leaderboards',
    ];
    return match ($role) {
        'owner' => ['*'],
        'admin' => array_values(array_diff(array_keys(permissionCatalog()), ['team.manage'])),
        'moderator' => array_values(array_unique([...$helper,
            'alerts.manage','messages.review','commands.view','messages.private','audit.view',
            'punishments.create','punishments.execute','profile.pins.manage','profile.files.manage',
        ])),
        default => $helper,
    };
}

function can(string $permission, ?array $user = null): bool
{
    $user ??= currentUser();
    if (!$user) {
        return false;
    }
    if ($permission === 'team.manage' && (string) $user['role'] !== 'owner') return false;
    $permissions = permissionsForRole((string) $user['role']);
    return in_array('*', $permissions, true) || in_array($permission, $permissions, true);
}

function requirePermission(string $permission): array
{
    $user = requireAuth();
    if (!can($permission, $user)) {
        http_response_code(403);
        renderErrorPage('Access denied', 'Your staff role does not allow this action.');
        exit;
    }
    return $user;
}

function audit(string $action, ?string $targetType = null, ?string $targetId = null, array $metadata = [], ?int $actorId = null, ?string $actorLabel = null): void
{
    $user = currentUser();
    $actorId ??= $user ? (int) $user['id'] : null;
    $actorLabel ??= $user ? (string) $user['email'] : null;

    $statement = db()->prepare(
        'INSERT INTO audit_logs (actor_user_id, actor_label, action, target_type, target_id, ip_hash, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
    );
    $statement->execute([
        $actorId,
        $actorLabel,
        $action,
        $targetType,
        $targetId,
        keyedHash(clientIp()),
        $metadata === [] ? null : jsonEncode($metadata),
        now(),
    ]);
}

function completeLogin(array $user): void
{
    session_regenerate_id(true);
    unset($_SESSION['preauth_user_id'], $_SESSION['preauth_started_at'], $_SESSION['pending_totp_secret'], $_SESSION['twofa_failures']);
    $_SESSION['user_id'] = (int) $user['id'];
    $_SESSION['session_version'] = (int) ($user['session_version'] ?? 1);
    $_SESSION['started_at'] = time();
    $_SESSION['last_activity'] = time();
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));

    $statement = db()->prepare('UPDATE users SET last_login_at = ?, updated_at = ? WHERE id = ?');
    $statement->execute([now(), now(), (int) $user['id']]);
    $clearFailures = db()->prepare('DELETE FROM login_attempts WHERE successful = 0 AND (identifier_hash = ? OR ip_hash = ?)');
    $clearFailures->execute([keyedHash((string) $user['email']), keyedHash(clientIp())]);
    recordLoginAttempt((string) $user['email'], true);
    audit('auth.login', 'user', (string) $user['id'], [], (int) $user['id'], (string) $user['email']);
}

function recordLoginAttempt(string $email, bool $successful): void
{
    $statement = db()->prepare('INSERT INTO login_attempts (identifier_hash, ip_hash, successful, attempted_at) VALUES (?, ?, ?, ?)');
    $statement->execute([keyedHash($email), keyedHash(clientIp()), $successful ? 1 : 0, now()]);
}

function loginBlocked(string $email): bool
{
    $cutoff = date('Y-m-d H:i:s', time() - 900);
    $statement = db()->prepare(
        'SELECT COUNT(*) FROM login_attempts WHERE successful = 0 AND attempted_at >= ? AND (identifier_hash = ? OR ip_hash = ?)'
    );
    $statement->execute([$cutoff, keyedHash($email), keyedHash(clientIp())]);
    return (int) $statement->fetchColumn() >= 7;
}

function passwordIssue(string $password): ?string
{
    if (strlen($password) < 12) {
        return 'Use at least 12 characters.';
    }
    if (!preg_match('/[a-z]/', $password) || !preg_match('/[A-Z]/', $password) || !preg_match('/\d/', $password) || !preg_match('/[^A-Za-z0-9]/', $password)) {
        return 'Use uppercase, lowercase, a number and a symbol.';
    }
    return null;
}

function passwordHash(string $password): string
{
    if (defined('PASSWORD_ARGON2ID')) {
        return password_hash($password, PASSWORD_ARGON2ID);
    }
    return password_hash($password, PASSWORD_DEFAULT);
}

function generateTemporaryPassword(int $length = 20): string
{
    $alphabet = 'abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#$%';
    $password = 'Aa1!';
    while (strlen($password) < $length) {
        $password .= $alphabet[random_int(0, strlen($alphabet) - 1)];
    }
    return secureShuffle($password);
}

function secureShuffle(string $value): string
{
    $characters = str_split($value);
    for ($i = count($characters) - 1; $i > 0; $i--) {
        $j = random_int(0, $i);
        [$characters[$i], $characters[$j]] = [$characters[$j], $characters[$i]];
    }
    return implode('', $characters);
}

function generateRecoveryCodes(int $count = 10): array
{
    $codes = [];
    for ($i = 0; $i < $count; $i++) {
        $raw = strtoupper(bin2hex(random_bytes(5)));
        $codes[] = substr($raw, 0, 5) . '-' . substr($raw, 5, 5);
    }
    return $codes;
}

function roleLabel(string $role): string
{
    return rolePresentation($role)['label'];
}

function displayDate(?string $value, string $fallback = 'Never'): string
{
    if (!$value) {
        return $fallback;
    }
    $time = strtotime($value);
    return $time === false ? $fallback : date('M j, Y · H:i', $time);
}

function renderAuthPage(string $title, string $content): void
{
    $flashes = takeFlashes();
    ?>
    <!doctype html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow,noarchive">
        <meta name="theme-color" content="#090c13">
        <title><?= e($title) ?> · Towny Reborn Staff</title>
        <link rel="icon" type="image/png" href="assets/favicon.png?v=4.1.0">
        <script src="assets/preferences.js?v=5.2.0"></script>
        <link rel="stylesheet" href="assets/app.css?v=5.2.0">
        <link rel="stylesheet" href="assets/workspace.css?v=5.2.0">
        <link rel="stylesheet" href="assets/experience.css?v=5.2.0">
        <script src="assets/workspace.js?v=5.2.0" defer></script>
        <script src="assets/app.js?v=5.2.0" defer></script>
        <script src="assets/experience.js?v=5.2.0" defer></script>
    </head>
    <body class="auth-body">
        <main class="auth-shell">
            <section class="auth-brand" aria-label="Towny Reborn Staff Portal">
                <img class="auth-logo" src="assets/logo.png?v=4.1.0" alt="Towny Reborn">
                <p>Secure moderation workspace</p>
            </section>
            <section class="auth-card">
                <?php foreach ($flashes as $flash): ?>
                    <div class="notice notice-<?= e($flash['type'] ?? 'info') ?>"><?= e($flash['message'] ?? '') ?></div>
                <?php endforeach; ?>
                <?= $content ?>
            </section>
        </main>
    </body>
    </html>
    <?php
}

function renderAppPage(string $title, string $activePage, string $content): void
{
    $user = requireAuth();
    $flashes = takeFlashes();
    $navigation = hqNavigation();
    $section='Workspace';
    foreach($navigation as $group=>$entries)foreach($entries as $entry)if($entry[0]===$activePage)$section=$group;
    ?>
    <!doctype html>
    <html lang="en">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <meta name="robots" content="noindex,nofollow,noarchive">
        <meta name="theme-color" content="#090c13">
        <title><?= e($title) ?> · Towny Reborn Staff</title>
        <link rel="icon" type="image/png" href="assets/favicon.png?v=4.1.0">
        <script src="assets/preferences.js?v=5.2.0"></script>
        <link rel="stylesheet" href="assets/app.css?v=5.2.0">
        <link rel="stylesheet" href="assets/workspace.css?v=5.2.0">
        <link rel="stylesheet" href="assets/experience.css?v=5.2.0">
        <script src="assets/workspace.js?v=5.2.0" defer></script>
        <script src="assets/app.js?v=5.2.0" defer></script>
        <script src="assets/experience.js?v=5.2.0" defer></script>
    </head>
    <body class="app-body" data-session-timeout="3600" data-csrf-token="<?= e(csrfToken()) ?>">
        <button class="mobile-menu" type="button" data-menu-toggle aria-label="Open navigation" aria-controls="hq-navigation" aria-expanded="false">Menu</button>
        <aside class="sidebar" id="hq-navigation" data-sidebar>
            <a class="sidebar-brand" href="index.php?page=dashboard">
                <img class="sidebar-logo" src="assets/logo.png?v=4.1.0" alt="Towny Reborn">
                <span class="sidebar-brand-label">Staff HQ</span>
            </a>
            <nav class="sidebar-nav" aria-label="Staff navigation">
                <?php foreach($navigation as $group=>$entries): $visible=array_filter($entries,static fn($entry)=>can($entry[2],$user)&&($entry[0]!=='chat-review'||can('messages.view',$user)));if(!$visible)continue; ?>
                    <div class="nav-group"><span class="nav-section"><?= e($group) ?></span>
                    <?php foreach($visible as [$page,$label,$permission,$icon]): ?>
                        <a href="index.php?page=<?= e($page) ?>" class="<?= $activePage===$page?'active':'' ?>" <?= $activePage===$page?'aria-current="page"':'' ?>><?= hqIcon($icon) ?><span><?= e($label) ?></span></a>
                    <?php endforeach; ?></div>
                <?php endforeach; ?>
            </nav>
            <details class="appearance-pane"><summary>Appearance <span aria-hidden="true">✿</span></summary><div class="appearance-controls"><label for="theme-choice">Theme</label><select id="theme-choice" data-theme-choice><option value="dark">Towny Dark</option><option value="cherry">Cherry Blossom</option><option value="light">Clear Light</option></select><label class="check-row"><input type="checkbox" data-large-text> Larger text</label></div></details>
            <div class="sidebar-user">
                <?php if (!empty($user['minecraft_username'])): ?><img class="avatar skin-avatar" src="<?= e(avatarUrl((string) ($user['minecraft_uuid'] ?? ''), (string) $user['minecraft_username'], 34)) ?>" data-player-avatar data-avatar-name="<?= e((string) $user['minecraft_username']) ?>" data-avatar-uuid="<?= e(normalizeUuid((string) ($user['minecraft_uuid'] ?? '')) ?? '') ?>" data-avatar-size="34" alt="<?= e((string) $user['minecraft_username']) ?> Minecraft skin"><?php else: ?><div class="avatar"><?= e(strtoupper(substr((string) $user['display_name'], 0, 1))) ?></div><?php endif; ?>
                <div><strong><?= e($user['display_name']) ?></strong><span><?= e(roleLabel((string) $user['role'])) ?></span></div>
                <form id="session-logout-form" method="post" action="index.php?page=logout">
                    <?= csrfField() ?>
                    <button class="icon-button" type="submit" title="Sign out" aria-label="Sign out">↗</button>
                </form>
            </div>
        </aside>
        <button type="button" class="navigation-backdrop" data-nav-close aria-label="Close navigation" hidden></button>
        <main class="main-content" id="main-content">
            <header class="page-header">
                <div><span class="eyebrow">Towny Reborn · <?= e($section) ?></span><h1><?= e($title) ?></h1></div>
                <div class="secure-pill"><span></span> Secure session</div>
            </header>
            <?php foreach ($flashes as $flash): ?>
                <div class="notice notice-<?= e($flash['type'] ?? 'info') ?>"><?= e($flash['message'] ?? '') ?></div>
            <?php endforeach; ?>
            <?php notificationBanner(); ?>
            <?= $content ?>
        </main>
    </body>
    </html>
    <?php
}

function renderErrorPage(string $title, string $message): void
{
    ob_start();
    ?>
    <div class="empty-state"><strong><?= e($title) ?></strong><p><?= e($message) ?></p><a class="button" href="index.php?page=dashboard">Return to dashboard</a></div>
    <?php
    renderAppPage($title, '', (string) ob_get_clean());
}

require_once APP_ROOT . '/app/schema.php';
require_once APP_ROOT . '/app/features.php';
require_once APP_ROOT . '/app/observability.php';
require_once APP_ROOT . '/app/observability-pages.php';
require_once APP_ROOT . '/app/analytics-data.php';
require_once APP_ROOT . '/app/analytics-pages.php';
require_once APP_ROOT . '/app/notifications.php';
require_once APP_ROOT . '/app/notification-pages.php';
require_once APP_ROOT . '/app/workspace.php';
require_once APP_ROOT . '/app/game-insights.php';
require_once APP_ROOT . '/app/connection-health.php';
require_once APP_ROOT . '/app/journey.php';
require_once APP_ROOT . '/app/experience.php';
require_once APP_ROOT . '/app/guide-documents.php';
ensureSchemaCurrent(db());
