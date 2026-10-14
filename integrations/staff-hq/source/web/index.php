<?php
declare(strict_types=1);

require_once __DIR__ . '/app/bootstrap.php';

$page = (string) ($_GET['page'] ?? (currentUser() ? 'dashboard' : 'login'));

if ($_SERVER['REQUEST_METHOD'] === 'POST') {
    handlePostAction($page);
}

switch ($page) {
    case 'journey': showJourneyInsights(); break;
    case 'attention': showNotifications(); break;
    case 'leaderboards': showLeaderboards(); break;
    case 'crates': showCrateInsights(); break;
    case 'economy': showEconomyInsights(); break;
    case 'permissions': showPermissionView(); break;
    case 'staff-guide': showStaffGuide(); break;
    case 'dragon': case 'votes': case 'pinata': case 'fishing': case 'shops': case 'pets': showTopicInsights($page); break;
    case 'metrics': showStatisticsWorkspace(); break;
    case 'statistics':
        if(isset($_GET['metric'])||isset($_GET['plugin'])||isset($_GET['section']))showStatisticsWorkspace();
        else showGameOverview();
        break;
    case 'plugins': showPluginOverview(); break;
    case 'activity': showGameplayActivity(); break;
    case 'chat-review': showChatReview(); break;
    case 'diagnostics': showServerDiagnostics(); break;
    case 'stats-export': exportStatistics(); break;
    case 'retention-export': exportRetentionCohorts(); break;
    case 'login':
        showLogin();
        break;
    case 'change-password':
        showPasswordChange();
        break;
    case 'setup-2fa':
        showTotpSetup();
        break;
    case 'verify-2fa':
        showTotpVerification();
        break;
    case 'recovery-codes':
        showRecoveryCodes();
        break;
    case 'players':
        showPlayers();
        break;
    case 'player':
        showPlayerProfile();
        break;
    case 'player-lookup':
        showPlayerLookup();
        break;
    case 'session-ping':
        showSessionPing();
        break;
    case 'analytics':
        showAnalytics();
        break;
    case 'alerts':
        showAlerts();
        break;
    case 'cases':
        showCases();
        break;
    case 'punishments':
        showPunishments();
        break;
    case 'documents':
        showDocuments();
        break;
    case 'commands':
        showCommandLogs();
        break;
    case 'messages':
        showMessageLogs();
        break;
    case 'team':
        showTeam();
        break;
    case 'audit':
        showAudit();
        break;
    case 'settings':
        showSettings();
        break;
    default:
        showDashboard();
        break;
}

function handlePostAction(string $page): void
{
    verifyCsrf();
    $action = (string) ($_POST['action'] ?? $page);

    try {
        if (str_starts_with($action,'profile-')) { postProfileAction($action); return; }
        if ($action==='role-presentation') { postRolePresentation(); return; }
        if (str_starts_with($action,'notification-')) { postNotifications($action); return; }
        if (str_starts_with($action,'review-') || $action==='chat-rules') { postObservability($action); return; }
        switch ($action) {
            case 'login':
                postLogin();
                return;
            case 'logout':
                $user = currentUser();
                if ($user) {
                    audit('auth.logout', 'user', (string) $user['id']);
                }
                $_SESSION = [];
                session_regenerate_id(true);
                redirect('login');
            case 'change-password':
                postPasswordChange();
                return;
            case 'setup-2fa':
                postTotpSetup();
                return;
            case 'verify-2fa':
                postTotpVerification();
                return;
            case 'create-case':
                postCreateCase();
                return;
            case 'update-case':
                postUpdateCase();
                return;
            case 'add-case-note':
                postCaseNote();
                return;
            case 'add-evidence':
                postEvidence();
                return;
            case 'delete-evidence':
                postDeleteEvidence();
                return;
            case 'upload-attachment':
                postUploadAttachment();
                return;
            case 'delete-attachment':
                postDeleteAttachment();
                return;
            case 'add-case-players':
                postAddCasePlayers();
                return;
            case 'remove-case-player':
                postRemoveCasePlayer();
                return;
            case 'delete-case':
                postDeleteCase();
                return;
            case 'add-punishment':
                postPunishment();
                return;
            case 'update-alert':
                postAlertUpdate();
                return;
            case 'dismiss-all-alerts':
                postDismissAllAlerts();
                return;
            case 'create-document':
                postCreateDocument();
                return;
            case 'update-document':
                postUpdateDocument();
                return;
            case 'delete-document':
                postDeleteDocument();
                return;
            case 'import-document':
                postImportDocument();
                return;
            case 'create-user':
                postCreateUser();
                return;
            case 'update-user':
                postUpdateUser();
                return;
            case 'reset-user-security':
                postResetUserSecurity();
                return;
            case 'update-role-permissions':
                postRolePermissions();
                return;
            case 'rotate-api-token':
                postRotateApiToken();
                return;
            default:
                throw new RuntimeException('Unknown action.');
        }
    } catch (Throwable $exception) {
        if (wantsJsonResponse()) {
            http_response_code(422);
            header('Content-Type: application/json; charset=utf-8');
            header('Cache-Control: no-store');
            echo jsonEncode(['ok' => false, 'error' => $exception->getMessage()]);
            exit;
        }
        setFlash('error', $exception->getMessage());
        $returnPage = (string) ($_POST['return_page'] ?? $page);
        $params = [];
        if (!empty($_POST['case_id'])) {
            $params['case_id'] = (int) $_POST['case_id'];
        }
        if (!empty($_POST['document_id'])) {
            $params['document_id'] = (int) $_POST['document_id'];
        }
        if ($returnPage==='player' && !empty($_POST['player_uuid'])) $params['uuid']=normalizeUuid((string)$_POST['player_uuid']);
        redirect($returnPage, $params);
    }
}

function wantsJsonResponse(): bool
{
    return strtolower((string) ($_SERVER['HTTP_X_REQUESTED_WITH'] ?? '')) === 'xmlhttprequest';
}

function jsonActionSuccess(string $ownerType, int $ownerId, string $redirectUrl): never
{
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo jsonEncode(['ok' => true, 'owner_type' => $ownerType, 'owner_id' => $ownerId, 'redirect' => $redirectUrl]);
    exit;
}

function postLogin(): void
{
    if (currentUser()) {
        redirect('dashboard');
    }

    $email = strtolower(trim((string) ($_POST['email'] ?? '')));
    $password = (string) ($_POST['password'] ?? '');
    if (!filter_var($email, FILTER_VALIDATE_EMAIL) || $password === '') {
        throw new RuntimeException('Enter a valid email address and password.');
    }
    if (loginBlocked($email)) {
        audit('auth.rate_limited', 'email', keyedHash($email), [], null, $email);
        throw new RuntimeException('Too many failed attempts. Wait 15 minutes and try again.');
    }

    $statement = db()->prepare('SELECT * FROM users WHERE email = ? LIMIT 1');
    $statement->execute([$email]);
    $user = $statement->fetch();
    $valid = $user && (bool) $user['active'] && password_verify($password, (string) $user['password_hash']);

    if (!$valid) {
        recordLoginAttempt($email, false);
        throw new RuntimeException('The email address or password is incorrect.');
    }

    if (password_needs_rehash((string) $user['password_hash'], defined('PASSWORD_ARGON2ID') ? PASSWORD_ARGON2ID : PASSWORD_DEFAULT)) {
        $update = db()->prepare('UPDATE users SET password_hash = ?, updated_at = ? WHERE id = ?');
        $update->execute([passwordHash($password), now(), (int) $user['id']]);
    }

    session_regenerate_id(true);
    unset($_SESSION['pending_totp_secret'], $_SESSION['twofa_failures'], $_SESSION['new_recovery_codes']);
    $_SESSION['preauth_user_id'] = (int) $user['id'];
    $_SESSION['preauth_started_at'] = time();
    $_SESSION['csrf_token'] = bin2hex(random_bytes(32));

    if ((bool) $user['must_change_password']) {
        redirect('change-password');
    }
    if (!(bool) $user['totp_enabled']) {
        redirect('setup-2fa');
    }
    redirect('verify-2fa');
}

function postPasswordChange(): void
{
    $user = preauthUser();
    if (!$user) {
        redirect('login');
    }
    $password = (string) ($_POST['password'] ?? '');
    $confirmation = (string) ($_POST['password_confirmation'] ?? '');
    if ($password !== $confirmation) {
        throw new RuntimeException('The password confirmation does not match.');
    }
    if ($issue = passwordIssue($password)) {
        throw new RuntimeException($issue);
    }
    if (password_verify($password, (string) $user['password_hash'])) {
        throw new RuntimeException('Choose a password different from the temporary password.');
    }

    $statement = db()->prepare('UPDATE users SET password_hash = ?, must_change_password = 0, updated_at = ? WHERE id = ?');
    $statement->execute([passwordHash($password), now(), (int) $user['id']]);
    audit('auth.password_changed', 'user', (string) $user['id'], [], (int) $user['id'], (string) $user['email']);
    setFlash('success', 'Password changed. Set up two-factor authentication to continue.');
    redirect((bool) $user['totp_enabled'] ? 'verify-2fa' : 'setup-2fa');
}

function postTotpSetup(): void
{
    $user = preauthUser();
    if (!$user || (bool) $user['must_change_password']) {
        redirect('login');
    }
    $secret = (string) ($_SESSION['pending_totp_secret'] ?? '');
    if ($secret === '') {
        throw new RuntimeException('The setup key expired. Refresh the page and try again.');
    }
    $code = (string) ($_POST['code'] ?? '');
    if (!Totp::verify($secret, $code)) {
        recordLoginAttempt((string) $user['email'], false);
        $_SESSION['twofa_failures'] = (int) ($_SESSION['twofa_failures'] ?? 0) + 1;
        if ((int) $_SESSION['twofa_failures'] >= 8) {
            unset($_SESSION['preauth_user_id'], $_SESSION['preauth_started_at'], $_SESSION['pending_totp_secret'], $_SESSION['twofa_failures']);
            setFlash('error', 'Too many invalid authentication codes. Sign in again.');
            redirect('login');
        }
        throw new RuntimeException('The six-digit code is invalid. Check the phone time and try again.');
    }

    $recoveryCodes = generateRecoveryCodes();
    $recoveryHashes = array_map(static fn(string $value): string => hash('sha256', strtoupper($value)), $recoveryCodes);
    $statement = db()->prepare('UPDATE users SET totp_secret_encrypted = ?, totp_enabled = 1, recovery_codes_json = ?, updated_at = ? WHERE id = ?');
    $statement->execute([encryptValue($secret), jsonEncode($recoveryHashes), now(), (int) $user['id']]);
    $_SESSION['new_recovery_codes'] = $recoveryCodes;
    audit('auth.2fa_enabled', 'user', (string) $user['id'], [], (int) $user['id'], (string) $user['email']);
    $user['totp_enabled'] = 1;
    completeLogin($user);
    redirect('recovery-codes');
}

function postTotpVerification(): void
{
    $user = preauthUser();
    if (!$user || !(bool) $user['totp_enabled']) {
        redirect('login');
    }
    $code = strtoupper(trim((string) ($_POST['code'] ?? '')));
    $valid = false;
    if (preg_match('/^\d{6}$/', $code)) {
        $valid = Totp::verify(decryptValue((string) $user['totp_secret_encrypted']), $code);
    } else {
        $hashes = json_decode((string) ($user['recovery_codes_json'] ?? '[]'), true);
        $hashes = is_array($hashes) ? $hashes : [];
        $candidate = hash('sha256', $code);
        $position = array_search($candidate, $hashes, true);
        if ($position !== false) {
            unset($hashes[$position]);
            $statement = db()->prepare('UPDATE users SET recovery_codes_json = ?, updated_at = ? WHERE id = ?');
            $statement->execute([jsonEncode(array_values($hashes)), now(), (int) $user['id']]);
            $valid = true;
            audit('auth.recovery_code_used', 'user', (string) $user['id'], ['remaining' => count($hashes)], (int) $user['id'], (string) $user['email']);
        }
    }

    if (!$valid) {
        recordLoginAttempt((string) $user['email'], false);
        $_SESSION['twofa_failures'] = (int) ($_SESSION['twofa_failures'] ?? 0) + 1;
        if ((int) $_SESSION['twofa_failures'] >= 8) {
            unset($_SESSION['preauth_user_id'], $_SESSION['preauth_started_at'], $_SESSION['pending_totp_secret'], $_SESSION['twofa_failures']);
            setFlash('error', 'Too many invalid authentication codes. Sign in again.');
            redirect('login');
        }
        throw new RuntimeException('The authentication or recovery code is invalid.');
    }
    completeLogin($user);
    redirect('dashboard');
}

function postCreateCase(): void
{
    $user = requirePermission('cases.create');
    $players = postedPlayers();
    $title = trim((string) ($_POST['title'] ?? ''));
    $description = trim((string) ($_POST['description'] ?? ''));
    $priority = (string) ($_POST['priority'] ?? 'normal');
    if ($players === [] || $title === '' || $description === '') {
        throw new RuntimeException('Select at least one player and enter a title and description.');
    }
    if (!in_array($priority, ['low', 'normal', 'high', 'critical'], true)) {
        $priority = 'normal';
    }

    $primary = $players[0];
    $caseNumber = 'TR-' . date('ymd') . '-' . strtoupper(bin2hex(random_bytes(3)));
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $statement = $pdo->prepare(
            'INSERT INTO cases (case_number, player_uuid, player_name, title, description, status, priority, created_by, assigned_user_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $statement->execute([$caseNumber, $primary['uuid'], $primary['username'], $title, $description, 'open', $priority, (int) $user['id'], (int) $user['id'], now(), now()]);
        $caseId = (int) $pdo->lastInsertId();
        addPlayersToCase($caseId, $players, true);
        $event = $pdo->prepare('INSERT INTO case_events (case_id, actor_user_id, event_type, body, created_at) VALUES (?, ?, ?, ?, ?)');
        $event->execute([$caseId, (int) $user['id'], 'created', 'Case created with ' . count($players) . ' subject(s)', now()]);
        storeUploadedAttachments('case', $caseId, (int) $user['id']);
        audit('case.created', 'case', (string) $caseId, ['case_number' => $caseNumber, 'players' => array_column($players, 'username')]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if (isset($caseId) && $caseId > 0) deleteOwnerAttachments('case', $caseId);
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }
    if (wantsJsonResponse()) {
        jsonActionSuccess('case', $caseId, 'index.php?page=cases&case_id=' . $caseId);
    }
    setFlash('success', 'Case ' . $caseNumber . ' created.');
    redirect('cases', ['case_id' => $caseId]);
}

function postUpdateCase(): void
{
    requirePermission('cases.manage');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $status = (string) ($_POST['status'] ?? 'open');
    $priority = (string) ($_POST['priority'] ?? 'normal');
    $assignee = (int) ($_POST['assigned_user_id'] ?? 0);
    if (!in_array($status, ['open', 'in_progress', 'pending', 'resolved', 'closed'], true)) {
        throw new RuntimeException('Invalid case status.');
    }
    if (!in_array($priority, ['low', 'normal', 'high', 'critical'], true)) {
        throw new RuntimeException('Invalid case priority.');
    }
    $resolvedAt = in_array($status, ['resolved', 'closed'], true) ? now() : null;
    $statement = db()->prepare('UPDATE cases SET status = ?, priority = ?, assigned_user_id = ?, resolved_at = ?, updated_at = ? WHERE id = ?');
    $statement->execute([$status, $priority, $assignee ?: null, $resolvedAt, now(), $caseId]);
    audit('case.updated', 'case', (string) $caseId, ['status' => $status, 'priority' => $priority, 'assignee' => $assignee]);
    setFlash('success', 'Case updated.');
    redirect('cases', ['case_id' => $caseId]);
}

function postCaseNote(): void
{
    $user = requirePermission('cases.create');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $body = trim((string) ($_POST['body'] ?? ''));
    if ($caseId <= 0 || $body === '') {
        throw new RuntimeException('Write a note before saving.');
    }
    $statement = db()->prepare('INSERT INTO case_events (case_id, actor_user_id, event_type, body, created_at) VALUES (?, ?, ?, ?, ?)');
    $statement->execute([$caseId, (int) $user['id'], 'note', $body, now()]);
    db()->prepare('UPDATE cases SET updated_at = ? WHERE id = ?')->execute([now(), $caseId]);
    audit('case.note_added', 'case', (string) $caseId);
    setFlash('success', 'Note added.');
    redirect('cases', ['case_id' => $caseId]);
}

function postEvidence(): void
{
    $user = requirePermission('cases.manage');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $title = trim((string) ($_POST['title'] ?? ''));
    $url = trim((string) ($_POST['url'] ?? ''));
    $description = trim((string) ($_POST['description'] ?? ''));
    if ($caseId <= 0 || $title === '' || !filter_var($url, FILTER_VALIDATE_URL) || !preg_match('#^https?://#i', $url)) {
        throw new RuntimeException('Enter an evidence title and a valid HTTPS or HTTP URL.');
    }
    $statement = db()->prepare('INSERT INTO evidence (case_id, evidence_type, title, url, description, added_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)');
    $statement->execute([$caseId, 'link', $title, $url, $description ?: null, (int) $user['id'], now()]);
    audit('case.evidence_added', 'case', (string) $caseId, ['title' => $title]);
    setFlash('success', 'Evidence attached.');
    redirect('cases', ['case_id' => $caseId]);
}

function postDeleteEvidence(): void
{
    requirePermission('cases.manage');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $evidenceId = (int) ($_POST['evidence_id'] ?? 0);
    $statement = db()->prepare('DELETE FROM evidence WHERE id = ? AND case_id = ?');
    $statement->execute([$evidenceId, $caseId]);
    if ($statement->rowCount() > 0) audit('case.evidence_deleted', 'case', (string) $caseId, ['evidence_id' => $evidenceId]);
    setFlash('success', 'Evidence link deleted.');
    redirect('cases', ['case_id' => $caseId]);
}

function postUploadAttachment(): void
{
    $ownerType = (string) ($_POST['owner_type'] ?? '');
    $ownerId = (int) ($_POST['owner_id'] ?? 0);
    $user = requirePermission($ownerType === 'document' ? 'documents.manage' : 'cases.manage');
    if (!in_array($ownerType, ['case', 'document'], true) || $ownerId <= 0) throw new RuntimeException('Invalid upload destination.');
    $table = $ownerType === 'document' ? 'documents' : 'cases';
    $statement = db()->prepare("SELECT COUNT(*) FROM `$table` WHERE id = ?");
    $statement->execute([$ownerId]);
    if ((int) $statement->fetchColumn() === 0) throw new RuntimeException('The destination no longer exists.');
    $count = storeUploadedAttachments($ownerType, $ownerId, (int) $user['id']);
    if ($count < 1) throw new RuntimeException('Choose at least one file to upload.');
    audit($ownerType . '.attachments_uploaded', $ownerType, (string) $ownerId, ['count' => $count]);
    setFlash('success', $count . ' file' . ($count === 1 ? '' : 's') . ' uploaded.');
    redirect($ownerType === 'document' ? 'documents' : 'cases', [$ownerType . '_id' => $ownerId]);
}

function postDeleteAttachment(): void
{
    $attachmentId = (int) ($_POST['attachment_id'] ?? 0);
    $statement = db()->prepare('SELECT owner_type, owner_id, original_name FROM attachments WHERE id = ?');
    $statement->execute([$attachmentId]);
    $attachment = $statement->fetch();
    if (!$attachment) throw new RuntimeException('Attachment not found.');
    $ownerType = (string) $attachment['owner_type'];
    if ($ownerType==='player') requirePermission('players.view');
    requirePermission(match($ownerType) { 'document'=>'documents.manage','player'=>'profile.files.manage','case'=>'cases.manage',default=>'__invalid' });
    if (!deleteAttachmentRecord($attachmentId, $ownerType, (int) $attachment['owner_id'])) throw new RuntimeException('Attachment could not be deleted.');
    audit($ownerType . '.attachment_deleted', $ownerType, (string) $attachment['owner_id'], ['name' => $attachment['original_name']]);
    setFlash('success', 'Attachment deleted.');
    if ($ownerType==='player') redirect('player',['uuid'=>dossierPlayer((int)$attachment['owner_id'])['uuid']]);
    redirect($ownerType === 'document' ? 'documents' : 'cases', [$ownerType . '_id' => (int) $attachment['owner_id']]);
}

function postAddCasePlayers(): void
{
    $user = requirePermission('cases.manage');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $players = postedPlayers('additional_players_json');
    if ($caseId <= 0 || $players === []) throw new RuntimeException('Select at least one player to add.');
    $exists = db()->prepare('SELECT COUNT(*) FROM cases WHERE id = ?');
    $exists->execute([$caseId]);
    if ((int) $exists->fetchColumn() === 0) throw new RuntimeException('Case not found.');
    addPlayersToCase($caseId, $players);
    db()->prepare('INSERT INTO case_events (case_id, actor_user_id, event_type, body, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?)')
        ->execute([$caseId, (int) $user['id'], 'subjects_added', 'Added ' . count($players) . ' player(s)', jsonEncode(['players' => array_column($players, 'username')]), now()]);
    db()->prepare('UPDATE cases SET updated_at = ? WHERE id = ?')->execute([now(), $caseId]);
    audit('case.subjects_added', 'case', (string) $caseId, ['players' => array_column($players, 'username')]);
    setFlash('success', 'Players added to the case.');
    redirect('cases', ['case_id' => $caseId]);
}

function postRemoveCasePlayer(): void
{
    $user = requirePermission('cases.manage');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $subjectId = (int) ($_POST['case_player_id'] ?? 0);
    $subjects = casePlayers($caseId);
    if (count($subjects) <= 1) throw new RuntimeException('A case must keep at least one player.');
    $removed = null;
    foreach ($subjects as $subject) if ((int) $subject['id'] === $subjectId) $removed = $subject;
    if (!$removed) throw new RuntimeException('Player is not part of this case.');
    db()->prepare('DELETE FROM case_players WHERE id = ? AND case_id = ?')->execute([$subjectId, $caseId]);
    if ((bool) $removed['primary_subject']) {
        $remaining = casePlayers($caseId)[0];
        db()->prepare('UPDATE case_players SET primary_subject = 1 WHERE id = ?')->execute([(int) $remaining['id']]);
        db()->prepare('UPDATE cases SET player_uuid = ?, player_name = ?, updated_at = ? WHERE id = ?')
            ->execute([$remaining['player_uuid'], $remaining['player_name'], now(), $caseId]);
    }
    db()->prepare('INSERT INTO case_events (case_id, actor_user_id, event_type, body, created_at) VALUES (?, ?, ?, ?, ?)')
        ->execute([$caseId, (int) $user['id'], 'subject_removed', 'Removed ' . $removed['player_name'] . ' from the case', now()]);
    audit('case.subject_removed', 'case', (string) $caseId, ['player' => $removed['player_name']]);
    setFlash('success', 'Player removed from the case.');
    redirect('cases', ['case_id' => $caseId]);
}

function postDeleteCase(): void
{
    $user = requirePermission('cases.manage');
    if (!in_array((string) $user['role'], ['owner', 'admin'], true)) throw new RuntimeException('Only Owners and Administrators can permanently delete a case.');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $statement = db()->prepare('SELECT case_number FROM cases WHERE id = ?');
    $statement->execute([$caseId]);
    $caseNumber = $statement->fetchColumn();
    if ($caseNumber === false) throw new RuntimeException('Case not found.');
    deleteOwnerAttachments('case', $caseId);
    db()->prepare('DELETE FROM cases WHERE id = ?')->execute([$caseId]);
    audit('case.deleted', 'case', (string) $caseId, ['case_number' => $caseNumber]);
    setFlash('success', 'Case ' . $caseNumber . ' permanently deleted.');
    redirect('cases');
}

function postPunishment(): void
{
    $user = requirePermission('punishments.create');
    $caseId = (int) ($_POST['case_id'] ?? 0);
    $playerName = trim((string) ($_POST['player_name'] ?? ''));
    $playerUuid = normalizeUuid((string) ($_POST['player_uuid'] ?? ''));
    $casePlayerId = (int) ($_POST['case_player_id'] ?? 0);
    if ($caseId > 0 && $casePlayerId > 0) {
        $subject = db()->prepare('SELECT * FROM case_players WHERE id = ? AND case_id = ?');
        $subject->execute([$casePlayerId, $caseId]);
        $selected = $subject->fetch();
        if (!$selected) throw new RuntimeException('Select a player from this case.');
        $playerName = (string) $selected['player_name'];
        $playerUuid = normalizeUuid((string) ($selected['player_uuid'] ?? ''));
    }
    $type = (string) ($_POST['punishment_type'] ?? 'warning');
    $reason = trim(preg_replace('/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/u', '', (string) ($_POST['reason'] ?? '')) ?? '');
    $duration = trim((string) ($_POST['duration_seconds'] ?? ''));
    if (!in_array($type, ['warning', 'kick', 'mute', 'tempban', 'ban'], true)) {
        throw new RuntimeException('Invalid punishment type.');
    }
    if ($playerName === '' || $reason === '' || mb_strlen($reason) > 1000) {
        throw new RuntimeException('Player and reason are required.');
    }
    if (!preg_match('/^[A-Za-z0-9_.-]{1,32}$/', $playerName)) throw new RuntimeException('The player name is not safe to send to the server.');
    $durationSeconds = $duration !== '' ? max(0, (int) $duration) : null;
    if ($type === 'tempban' && (!$durationSeconds || $durationSeconds < 1)) {
        throw new RuntimeException('A temporary ban requires a duration in seconds.');
    }
    $expiresAt = $durationSeconds ? date('Y-m-d H:i:s', time() + $durationSeconds) : null;
    $execute = isset($_POST['execute_on_server']);
    if ($execute && !can('punishments.execute', $user)) throw new RuntimeException('Your role cannot execute server punishments.');

    $pdo = db();
    $pdo->beginTransaction();
    try {
        $statement = $pdo->prepare(
            'INSERT INTO punishments (case_id, player_uuid, player_name, punishment_type, reason, duration_seconds, source, issued_by, issued_by_label, issued_at, expires_at, execution_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
        );
        $statement->execute([
            $caseId ?: null, $playerUuid, $playerName, $type, $reason, $durationSeconds,
            $execute ? 'staff_hq' : 'panel_record', (int) $user['id'], (string) $user['display_name'],
            now(), $expiresAt, $execute ? 'pending' : 'recorded',
        ]);
        $punishmentId = (int) $pdo->lastInsertId();
        $queueId = null;
        if ($execute) {
            $queue = $pdo->prepare(
                'INSERT INTO action_queue (punishment_id, action_type, player_uuid, player_name, reason, duration_seconds, target_server, requested_by, requested_at, status)
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)'
            );
            $queue->execute([$punishmentId, $type, $playerUuid, $playerName, $reason, $durationSeconds, 'smp', (int) $user['id'], now(), 'pending']);
            $queueId = (int) $pdo->lastInsertId();
            $pdo->prepare('UPDATE punishments SET external_id = ? WHERE id = ?')->execute(['web-action-' . $queueId, $punishmentId]);
        }
        if ($caseId > 0) {
            $event = $pdo->prepare('INSERT INTO case_events (case_id, actor_user_id, event_type, body, metadata_json, created_at) VALUES (?, ?, ?, ?, ?, ?)');
            $event->execute([$caseId, (int) $user['id'], 'punishment', ucfirst($type) . ': ' . $reason, jsonEncode(['duration_seconds' => $durationSeconds, 'queued' => $execute]), now()]);
        }
        audit($execute ? 'punishment.queued' : 'punishment.recorded', 'player', $playerUuid ?: $playerName, ['type' => $type, 'case_id' => $caseId ?: null, 'queue_id' => $queueId]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }

    setFlash('success', $execute ? 'Action queued securely for the SMP connector.' : 'Punishment decision recorded.');
    if ($caseId > 0) redirect('cases', ['case_id' => $caseId]);
    if ((string) ($_POST['return_page'] ?? '') === 'player') redirect('player', ['uuid' => $playerUuid, 'name' => $playerName]);
    redirect('punishments');
}

function postAlertUpdate(): void
{
    $user = requirePermission('alerts.manage');
    $alertId = (int) ($_POST['alert_id'] ?? 0);
    $status = (string) ($_POST['status'] ?? 'reviewed');
    if (!in_array($status, ['open', 'reviewed', 'dismissed', 'escalated'], true)) {
        throw new RuntimeException('Invalid alert status.');
    }
    $statement = db()->prepare('UPDATE alerts SET status = ?, assigned_user_id = ?, reviewed_at = ? WHERE id = ?');
    $statement->execute([$status, (int) $user['id'], $status === 'open' ? null : now(), $alertId]);
    audit('alert.updated', 'alert', (string) $alertId, ['status' => $status]);
    setFlash('success', 'Alert updated.');
    redirect('alerts');
}

function postDismissAllAlerts(): void
{
    $user = requirePermission('alerts.manage');
    $statement = db()->prepare("UPDATE alerts SET status = 'dismissed', assigned_user_id = ?, reviewed_at = ? WHERE status <> 'dismissed'");
    $statement->execute([(int) $user['id'], now()]);
    $count = $statement->rowCount();
    audit('alerts.dismissed_all', 'alerts', 'all', ['count' => $count]);
    setFlash('success', $count . ' alert' . ($count === 1 ? '' : 's') . ' dismissed.');
    redirect('alerts', ['status' => 'active']);
}

function postCreateDocument(): void
{
    $user = requirePermission('documents.manage');
    $title = trim((string) ($_POST['title'] ?? ''));
    $category = trim((string) ($_POST['category'] ?? 'General'));
    $body = trim((string) ($_POST['body'] ?? ''));
    if ($title === '' || $category === '' || $body === '') {
        throw new RuntimeException('Title, category and document content are required.');
    }
    $pdo = db();
    $pdo->beginTransaction();
    try {
        $statement = $pdo->prepare('INSERT INTO documents (title, category, body, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
        $statement->execute([$title, $category, $body, (int) $user['id'], now(), now()]);
        $id = (int) $pdo->lastInsertId();
        storeUploadedAttachments('document', $id, (int) $user['id']);
        audit('document.created', 'document', (string) $id, ['title' => $title]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if (isset($id) && $id > 0) deleteOwnerAttachments('document', $id);
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }
    if (wantsJsonResponse()) {
        jsonActionSuccess('document', $id, 'index.php?page=documents&document_id=' . $id);
    }
    setFlash('success', 'Document created.');
    redirect('documents', ['document_id' => $id]);
}

function postUpdateDocument(): void
{
    $user = requirePermission('documents.manage');
    $id = (int) ($_POST['document_id'] ?? 0);
    $title = trim((string) ($_POST['title'] ?? ''));
    $category = trim((string) ($_POST['category'] ?? 'General'));
    $body = trim((string) ($_POST['body'] ?? ''));
    if ($id <= 0 || $title === '' || $category === '' || $body === '') throw new RuntimeException('Title, category and document content are required.');
    $statement = db()->prepare('UPDATE documents SET title = ?, category = ?, body = ?, updated_by = ?, updated_at = ? WHERE id = ?');
    $statement->execute([$title, $category, $body, (int) $user['id'], now(), $id]);
    if ($statement->rowCount() === 0) {
        $exists = db()->prepare('SELECT COUNT(*) FROM documents WHERE id = ?');
        $exists->execute([$id]);
        if ((int) $exists->fetchColumn() === 0) throw new RuntimeException('Document not found.');
    }
    storeUploadedAttachments('document', $id, (int) $user['id']);
    audit('document.updated', 'document', (string) $id, ['title' => $title]);
    if (wantsJsonResponse()) {
        jsonActionSuccess('document', $id, 'index.php?page=documents&document_id=' . $id);
    }
    setFlash('success', 'Document updated.');
    redirect('documents', ['document_id' => $id]);
}

function postDeleteDocument(): void
{
    requirePermission('documents.manage');
    $id = (int) ($_POST['document_id'] ?? 0);
    $statement = db()->prepare('SELECT title FROM documents WHERE id = ?');
    $statement->execute([$id]);
    $title = $statement->fetchColumn();
    if ($title === false) throw new RuntimeException('Document not found.');
    deleteOwnerAttachments('document', $id);
    db()->prepare('DELETE FROM documents WHERE id = ?')->execute([$id]);
    audit('document.deleted', 'document', (string) $id, ['title' => $title]);
    setFlash('success', 'Document deleted.');
    redirect('documents');
}

function postImportDocument(): void
{
    $user = requirePermission('documents.manage');
    $import = obtainDocumentImport();
    try {
        $fileName = (string) ($_FILES['import_file']['name'] ?? '');
        $isDocx = trim((string) ($_POST['google_docs_url'] ?? '')) !== ''
            || strtolower(pathinfo($fileName, PATHINFO_EXTENSION)) === 'docx';
        if ($isDocx) {
            $body = extractDocxText($import['path']);
        } else {
            $mime = (string) (new finfo(FILEINFO_MIME_TYPE))->file($import['path']);
            if (!in_array($mime, ['text/plain', 'text/markdown', 'application/octet-stream'], true)) {
                throw new RuntimeException('Only DOCX, Markdown and plain-text documents can be converted.');
            }
            $body = trim((string) file_get_contents($import['path']));
        }
    } finally {
        if ($import['temporary']) @unlink($import['path']);
    }
    if ($body === '' || strlen($body) > 16_000_000) throw new RuntimeException('The imported document is empty or too large for the knowledge base.');
    $title = trim((string) ($_POST['title'] ?? '')) ?: (string) $import['title'];
    $category = trim((string) ($_POST['category'] ?? 'Imported')) ?: 'Imported';
    $statement = db()->prepare('INSERT INTO documents (title, category, body, updated_by, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?)');
    $statement->execute([mb_substr($title, 0, 190), mb_substr($category, 0, 64), $body, (int) $user['id'], now(), now()]);
    $id = (int) db()->lastInsertId();
    audit('document.imported', 'document', (string) $id, ['title' => $title, 'google_docs' => trim((string) ($_POST['google_docs_url'] ?? '')) !== '']);
    setFlash('success', 'Document imported. Review the converted formatting and save any edits.');
    redirect('documents', ['document_id' => $id, 'edit' => 1]);
}

function postCreateUser(): void
{
    $actor = requirePermission('team.manage');
    if ((string) $actor['role'] !== 'owner') {
        throw new RuntimeException('Only the Owner can create staff accounts.');
    }
    $email = strtolower(trim((string) ($_POST['email'] ?? '')));
    $displayName = trim((string) ($_POST['display_name'] ?? ''));
    $role = (string) ($_POST['role'] ?? 'helper');
    $minecraftUsername = trim((string) ($_POST['minecraft_username'] ?? ''));
    $submittedUuid = trim((string) ($_POST['minecraft_uuid'] ?? ''));
    $minecraftUuid = normalizeUuid($submittedUuid);
    $_SESSION['new_staff_form'] = [
        'email' => mb_substr($email, 0, 190),
        'display_name' => mb_substr($displayName, 0, 80),
        'role' => in_array($role, ['owner', 'admin', 'moderator', 'helper'], true) ? $role : 'helper',
        'minecraft_username' => mb_substr($minecraftUsername, 0, 32),
        'minecraft_uuid' => $minecraftUuid,
    ];

    if (strlen($email) > 190 || filter_var($email, FILTER_VALIDATE_EMAIL) === false) {
        throw new RuntimeException('Enter a valid email address of at most 190 characters.');
    }
    if ($displayName === '' || mb_strlen($displayName) > 80) {
        throw new RuntimeException('Enter a display name between 1 and 80 characters.');
    }
    if (!in_array($role, ['owner', 'admin', 'moderator', 'helper'], true)) {
        throw new RuntimeException('Invalid staff role.');
    }
    $pdo = db();
    $existing = $pdo->prepare('SELECT id FROM users WHERE email = ? LIMIT 1');
    $existing->execute([$email]);
    if ($existing->fetchColumn() !== false) {
        throw new RuntimeException('A staff account with that email already exists. Use Manage to update it.');
    }
    if ($minecraftUsername !== '' || $submittedUuid !== '') {
        if ($submittedUuid !== '' && $minecraftUuid === null) {
            throw new RuntimeException('Select the Minecraft account from the player search results.');
        }
        $player = resolvePlayer($minecraftUsername, $minecraftUuid);
        $playerUuid = $player ? normalizeUuid((string) $player['uuid']) : null;
        if (!$player || !$playerUuid
            || ($minecraftUuid !== null && $minecraftUuid !== $playerUuid)
            || ($minecraftUsername !== '' && strcasecmp($minecraftUsername, (string) $player['username']) !== 0)) {
            throw new RuntimeException('Select the Minecraft account from the player search results.');
        }
        $minecraftUsername = (string) $player['username'];
        $minecraftUuid = $playerUuid;
        $linked = $pdo->prepare('SELECT id FROM users WHERE minecraft_uuid = ? LIMIT 1');
        $linked->execute([$minecraftUuid]);
        if ($linked->fetchColumn() !== false) {
            throw new RuntimeException('That Minecraft account is already linked to another staff member.');
        }
    }

    $password = generateTemporaryPassword();
    $passwordHash = passwordHash($password);
    $pdo->beginTransaction();
    try {
        $statement = $pdo->prepare(
            'INSERT INTO users (email, display_name, password_hash, role, minecraft_uuid, minecraft_username, must_change_password, totp_enabled, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, 1, 0, 1, ?, ?)'
        );
        $statement->execute([$email, $displayName, $passwordHash, $role, $minecraftUuid, $minecraftUsername ?: null, now(), now()]);
        $userId = (int) $pdo->lastInsertId();
        audit('staff.created', 'user', (string) $userId, [
            'email' => $email,
            'display_name' => $displayName,
            'role' => $role,
            'minecraft_username' => $minecraftUsername ?: null,
        ]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        if ($exception instanceof PDOException && (int) ($exception->errorInfo[1] ?? 0) === 1062) {
            throw new RuntimeException('A staff account with that email already exists. Use Manage to update it.', 0, $exception);
        }
        throw $exception;
    }

    unset($_SESSION['new_staff_form']);
    $_SESSION['one_time_staff_password'] = ['email' => $email, 'password' => $password];
    setFlash('success', 'Staff account created. Copy the temporary password below and share it with the new staff member.');
    redirect('team');
}

function postUpdateUser(): void
{
    $actor = requirePermission('team.manage');
    $userId = (int) ($_POST['user_id'] ?? 0);
    $role = (string) ($_POST['role'] ?? 'helper');
    $active = isset($_POST['active']) ? 1 : 0;
    $minecraftUsername = trim((string) ($_POST['minecraft_username'] ?? ''));
    $minecraftUuid = normalizeUuid((string) ($_POST['minecraft_uuid'] ?? ''));
    if (isset($_POST['unlink_minecraft'])) {
        $minecraftUsername = '';
        $minecraftUuid = null;
    } elseif ($minecraftUsername !== '' || $minecraftUuid !== null) {
        $player = resolvePlayer($minecraftUsername, $minecraftUuid);
        if (!$player) throw new RuntimeException('Select the Minecraft account from the player search results.');
        $minecraftUsername = (string) $player['username'];
        $minecraftUuid = normalizeUuid((string) $player['uuid']);
        $linked = db()->prepare('SELECT id FROM users WHERE minecraft_uuid = ? AND id <> ? LIMIT 1');
        $linked->execute([$minecraftUuid, $userId]);
        if ($linked->fetchColumn() !== false) throw new RuntimeException('That Minecraft account is already linked to another staff member.');
    }
    if (!in_array($role, ['owner', 'admin', 'moderator', 'helper'], true)) {
        throw new RuntimeException('Invalid staff role.');
    }
    $statement = db()->prepare('SELECT * FROM users WHERE id = ?');
    $statement->execute([$userId]);
    $target = $statement->fetch();
    if (!$target) {
        throw new RuntimeException('Staff account not found.');
    }
    if ((string) $actor['role'] !== 'owner' && ((string) $target['role'] === 'owner' || $role === 'owner')) {
        throw new RuntimeException('Only the Owner can change Owner accounts.');
    }
    if ($userId === (int) $actor['id'] && !$active) {
        throw new RuntimeException('You cannot deactivate your own account.');
    }
    if ((string) $target['role'] === 'owner' && ($role !== 'owner' || !$active)) {
        $activeOwners = (int) db()->query("SELECT COUNT(*) FROM users WHERE role = 'owner' AND active = 1")->fetchColumn();
        if ($activeOwners <= 1) {
            throw new RuntimeException('The last active Owner account cannot be demoted or disabled.');
        }
    }
    db()->prepare('UPDATE users SET role = ?, active = ?, minecraft_uuid = ?, minecraft_username = ?, updated_at = ? WHERE id = ?')
        ->execute([$role, $active, $minecraftUuid, $minecraftUsername ?: null, now(), $userId]);
    audit('staff.updated', 'user', (string) $userId, ['role' => $role, 'active' => $active, 'minecraft_username' => $minecraftUsername ?: null]);
    setFlash('success', 'Staff account updated.');
    redirect('team');
}

function postResetUserSecurity(): void
{
    $actor = requirePermission('team.manage');
    $userId = (int) ($_POST['user_id'] ?? 0);
    $statement = db()->prepare('SELECT * FROM users WHERE id = ?');
    $statement->execute([$userId]);
    $target = $statement->fetch();
    if (!$target) {
        throw new RuntimeException('Staff account not found.');
    }
    if ($userId === (int) $actor['id']) {
        throw new RuntimeException('For safety, another Administrator or Owner must reset your account.');
    }
    if ((string) $actor['role'] !== 'owner' && (string) $target['role'] === 'owner') {
        throw new RuntimeException('Only the Owner can reset an Owner account.');
    }
    $password = generateTemporaryPassword();
    db()->prepare('UPDATE users SET password_hash = ?, must_change_password = 1, totp_secret_encrypted = NULL, totp_enabled = 0, recovery_codes_json = NULL, session_version = session_version + 1, updated_at = ? WHERE id = ?')
        ->execute([passwordHash($password), now(), $userId]);
    audit('staff.security_reset', 'user', (string) $userId);
    $_SESSION['one_time_staff_password'] = ['email' => $target['email'], 'password' => $password];
    setFlash('warning', 'Security reset complete. Copy the new one-time password shown above the team list.');
    redirect('team');
}

function postRolePermissions(): void
{
    $actor = requirePermission('team.manage');
    if ((string) $actor['role'] !== 'owner') throw new RuntimeException('Only an Owner can change role visibility and permissions.');
    $role = (string) ($_POST['role'] ?? '');
    if (!in_array($role, ['admin', 'moderator', 'helper'], true)) throw new RuntimeException('Invalid configurable role.');
    $selected = $_POST['permissions'] ?? [];
    if (!is_array($selected)) $selected = [];
    $catalog = permissionCatalog();
    $statement = db()->prepare(
        'INSERT INTO role_permissions (role, permission, allowed, updated_by, updated_at) VALUES (?, ?, ?, ?, ?)
         ON DUPLICATE KEY UPDATE allowed = VALUES(allowed), updated_by = VALUES(updated_by), updated_at = VALUES(updated_at)'
    );
    $pdo = db();
    $pdo->beginTransaction();
    try {
        foreach ($catalog as $permission => $_details) {
            $statement->execute([$role, $permission, in_array($permission, $selected, true) ? 1 : 0, (int) $actor['id'], now()]);
        }
        audit('staff.role_permissions_updated', 'role', $role, ['permissions' => array_values(array_intersect(array_keys($catalog), $selected))]);
        $pdo->commit();
    } catch (Throwable $exception) {
        if ($pdo->inTransaction()) $pdo->rollBack();
        throw $exception;
    }
    setFlash('success', roleLabel($role) . ' visibility and permissions updated.');
    redirect('team', ['permissions_role' => $role]);
}

function postRotateApiToken(): void
{
    requirePermission('api.manage');
    $clientId = (int) ($_POST['client_id'] ?? 0);
    $token = 'tr_live_' . bin2hex(random_bytes(32));
    if ($clientId > 0) {
        $statement = db()->prepare('UPDATE api_clients SET token_prefix = ?, token_hash = ?, active = 1, rotated_at = ? WHERE id = ?');
        $statement->execute([substr($token, 0, 12), hash('sha256', $token), now(), $clientId]);
    } else {
        $statement = db()->prepare('INSERT INTO api_clients (client_name, token_prefix, token_hash, active, created_at) VALUES (?, ?, ?, 1, ?)');
        $statement->execute(['Towny Reborn Network', substr($token, 0, 12), hash('sha256', $token), now()]);
        $clientId = (int) db()->lastInsertId();
    }
    $_SESSION['new_api_token'] = $token;
    audit('api.token_rotated', 'api_client', (string) $clientId);
    setFlash('warning', 'API token rotated. Update every server connector before leaving this page.');
    redirect('settings');
}

function showLogin(): void
{
    if (currentUser()) {
        redirect('dashboard');
    }
    if (!empty($_SESSION['timed_out'])) {
        unset($_SESSION['timed_out']);
        setFlash('warning', 'You were signed out after 60 minutes without activity.');
    }
    ob_start();
    ?>
    <h1>Staff sign in</h1>
    <p>Use your approved staff account. Password and two-factor authentication are required.</p>
    <form class="form-stack" method="post" action="index.php?page=login">
        <?= csrfField() ?><input type="hidden" name="action" value="login">
        <div class="field"><label for="email">Email address</label><input id="email" name="email" type="email" autocomplete="username" required autofocus></div>
        <div class="field"><label for="password">Password</label><input id="password" name="password" type="password" autocomplete="current-password" required></div>
        <button class="button" type="submit">Continue securely</button>
    </form>
    <?php
    renderAuthPage('Sign in', (string) ob_get_clean());
}

function showPasswordChange(): void
{
    $user = preauthUser();
    if (!$user) {
        redirect('login');
    }
    ob_start();
    ?>
    <h1>Create your password</h1>
    <p>The temporary password cannot be used again. Choose a unique password with at least 12 characters.</p>
    <form class="form-stack" method="post" action="index.php?page=change-password">
        <?= csrfField() ?><input type="hidden" name="action" value="change-password">
        <div class="field"><label for="password">New password</label><input id="password" name="password" type="password" autocomplete="new-password" required><span class="field-hint">Uppercase, lowercase, number and symbol required.</span></div>
        <div class="field"><label for="password_confirmation">Confirm password</label><input id="password_confirmation" name="password_confirmation" type="password" autocomplete="new-password" required></div>
        <button class="button" type="submit">Save password</button>
    </form>
    <?php
    renderAuthPage('Change password', (string) ob_get_clean());
}

function showTotpSetup(): void
{
    $user = preauthUser();
    if (!$user || (bool) $user['must_change_password']) {
        redirect('login');
    }
    if ((bool) $user['totp_enabled']) {
        redirect('verify-2fa');
    }
    $_SESSION['pending_totp_secret'] ??= Totp::generateSecret();
    $secret = (string) $_SESSION['pending_totp_secret'];
    $uri = Totp::provisioningUri($secret, (string) $user['email'], 'Towny Reborn Staff');
    ob_start();
    ?>
    <h1>Protect your account</h1>
    <p>Add this account to Google Authenticator or another TOTP application, then enter the current six-digit code.</p>
    <div class="secret-box"><span class="field-hint">Manual setup key</span><code id="totp-secret"><?= e($secret) ?></code></div>
    <div class="button-row"><button class="button secondary small" type="button" data-copy="#totp-secret">Copy setup key</button><a class="button secondary small" href="<?= e($uri) ?>">Open authenticator app</a></div>
    <form class="form-stack" method="post" action="index.php?page=setup-2fa">
        <?= csrfField() ?><input type="hidden" name="action" value="setup-2fa">
        <div class="field"><label for="code">Six-digit code</label><input id="code" name="code" inputmode="numeric" autocomplete="one-time-code" pattern="[0-9]{6}" maxlength="6" required autofocus></div>
        <button class="button" type="submit">Enable two-factor authentication</button>
    </form>
    <?php
    renderAuthPage('Set up 2FA', (string) ob_get_clean());
}

function showTotpVerification(): void
{
    $user = preauthUser();
    if (!$user) {
        redirect('login');
    }
    ob_start();
    ?>
    <h1>Two-factor authentication</h1>
    <p>Enter the current six-digit code from your authenticator app. A recovery code also works.</p>
    <form class="form-stack" method="post" action="index.php?page=verify-2fa">
        <?= csrfField() ?><input type="hidden" name="action" value="verify-2fa">
        <div class="field"><label for="code">Authentication code</label><input id="code" name="code" autocomplete="one-time-code" required autofocus></div>
        <button class="button" type="submit">Verify and sign in</button>
    </form>
    <?php
    renderAuthPage('Verify 2FA', (string) ob_get_clean());
}

function showRecoveryCodes(): void
{
    requireAuth();
    $codes = $_SESSION['new_recovery_codes'] ?? null;
    if (!is_array($codes)) {
        redirect('dashboard');
    }
    unset($_SESSION['new_recovery_codes']);
    ob_start();
    ?>
    <h1>Save your recovery codes</h1>
    <p>Store these codes somewhere private. Each code works once if your phone is unavailable.</p>
    <div class="recovery-grid" id="recovery-codes"><?php foreach ($codes as $code): ?><code><?= e($code) ?></code><?php endforeach; ?></div>
    <div class="button-row"><button class="button secondary" type="button" data-copy="#recovery-codes">Copy all codes</button><a class="button" href="index.php?page=dashboard">I saved them</a></div>
    <?php
    renderAuthPage('Recovery codes', (string) ob_get_clean());
}

function showDashboard(): void
{
    showHqDashboard();
}

function showPlayers(): void
{
    requirePermission('players.view');
    $query = trim((string) ($_GET['q'] ?? ''));
    if ($query !== '') {
        $statement = db()->prepare('SELECT * FROM players WHERE username LIKE ? OR uuid = ? ORDER BY last_seen_at DESC LIMIT 100');
        $statement->execute(['%' . $query . '%', $query]);
        $players = $statement->fetchAll();
    } else {
        $players = db()->query('SELECT * FROM players ORDER BY online DESC, last_seen_at DESC LIMIT 100')->fetchAll();
    }
    ob_start();
    ?>
    <form class="toolbar" method="get"><input type="hidden" name="page" value="players"><div class="field"><label for="q">Search players</label><input id="q" name="q" value="<?= e($query) ?>" placeholder="Username or UUID"></div><button class="button secondary" type="submit">Search</button></form>
    <article class="panel"><div class="panel-header"><h2>Player directory</h2><span class="badge"><?= count($players) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Player</th><th>Platform</th><th>Status</th><th>Town</th><th>Balance</th><th>Risk</th><th>Last seen</th></tr></thead><tbody>
    <?php if ($players === []): ?><tr><td colspan="7"><div class="empty-state"><strong>No player data yet</strong><p>The server connector will populate this directory.</p></div></td></tr><?php endif; ?>
    <?php foreach ($players as $player): ?><tr><td><?= renderPlayerIdentity((string) $player['username'], (string) $player['uuid'], null, true) ?></td><td><?= e($player['platform']) ?></td><td><span class="badge <?= (bool) $player['online'] ? 'online' : '' ?>"><?= (bool) $player['online'] ? 'Online' : 'Offline' ?></span></td><td><?= e($player['town_name'] ?: '—') ?></td><td><?= $player['balance'] !== null ? '$' . number_format((float) $player['balance'], 2) : '—' ?></td><td><?= (int) $player['risk_score'] ?></td><td><?= e(displayDate($player['last_seen_at'])) ?></td></tr><?php endforeach; ?>
    </tbody></table></div></div></article>
    <?php
    renderAppPage('Players', 'players', (string) ob_get_clean());
}

function showPlayerProfile(): void
{
    showPlayerWorkspace();
}

function showPlayerLookup(): never
{
    requirePermission('players.view');
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    $query = trim((string) ($_GET['q'] ?? ''));
    if (mb_strlen($query) < 1) {
        echo jsonEncode(['players' => []]);
        exit;
    }
    $normalized = normalizeUuid($query);
    $statement = db()->prepare(
        'SELECT uuid, username, platform, online, town_name, last_seen_at FROM players
         WHERE username LIKE ? OR uuid = ? ORDER BY (LOWER(username) = LOWER(?)) DESC, online DESC, last_seen_at DESC LIMIT 12'
    );
    $statement->execute(['%' . $query . '%', $normalized ?: $query, $query]);
    $players = [];
    foreach ($statement->fetchAll() as $player) {
        $players[] = [
            'uuid' => normalizeUuid((string) $player['uuid']),
            'username' => (string) $player['username'],
            'platform' => (string) $player['platform'],
            'online' => (bool) $player['online'],
            'town' => $player['town_name'],
            'last_seen' => displayDate($player['last_seen_at']),
            'avatar' => avatarUrl((string) $player['uuid'], (string) $player['username'], 48),
        ];
    }
    echo jsonEncode(['players' => $players]);
    exit;
}

function showSessionPing(): never
{
    requireAuth();
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo jsonEncode(['ok' => true, 'expires_in' => 3600]);
    exit;
}

function showAnalytics(): void
{
    showAnalyticsWorkspace();
}

function showAlerts(): void
{
    requirePermission('alerts.view');
    $status = (string) ($_GET['status'] ?? 'active');
    if ($status === 'all') {
        $alerts = db()->query('SELECT * FROM alerts ORDER BY occurred_at DESC LIMIT 200')->fetchAll();
    } elseif (in_array($status, ['open', 'reviewed', 'dismissed', 'escalated'], true)) {
        $statement = db()->prepare('SELECT * FROM alerts WHERE status = ? ORDER BY occurred_at DESC LIMIT 200');
        $statement->execute([$status]);
        $alerts = $statement->fetchAll();
    } else {
        $alerts = db()->query("SELECT * FROM alerts WHERE status IN ('open','escalated') ORDER BY occurred_at DESC LIMIT 200")->fetchAll();
    }
    ob_start();
    ?>
    <div class="toolbar alert-toolbar"><div class="button-row"><a class="button secondary small" href="index.php?page=alerts&status=active">Active</a><a class="button secondary small" href="index.php?page=alerts&status=reviewed">Reviewed</a><a class="button secondary small" href="index.php?page=alerts&status=dismissed">Dismissed</a><a class="button secondary small" href="index.php?page=alerts&status=all">All</a></div><?php if (can('alerts.manage')): ?><form method="post" action="index.php?page=alerts"><?= csrfField() ?><input type="hidden" name="action" value="dismiss-all-alerts"><input type="hidden" name="return_page" value="alerts"><button class="button danger small" type="submit" data-confirm="Dismiss every alert that is not already dismissed?">Dismiss all alerts</button></form><?php endif; ?></div>
    <article class="panel"><div class="panel-header"><h2>Vulcan and network alerts</h2><span class="badge"><?= count($alerts) ?> shown</span></div><div class="panel-body flush"><?php renderAlertsTable($alerts, true); ?></div></article>
    <?php
    renderAppPage('Alerts', 'alerts', (string) ob_get_clean());
}

function showCases(): void
{
    requirePermission('cases.view');
    $caseId = (int) ($_GET['case_id'] ?? 0);
    if ($caseId > 0) {
        showCaseDetail($caseId);
        return;
    }
    $cases = db()->query('SELECT c.*, u.display_name AS assigned_name FROM cases c LEFT JOIN users u ON u.id = c.assigned_user_id ORDER BY c.updated_at DESC LIMIT 200')->fetchAll();
    $subjectMap = casePlayersMap(array_map(static fn(array $case): int => (int) $case['id'], $cases));
    foreach ($cases as &$case) $case['subjects'] = $subjectMap[(int) $case['id']] ?? [];
    unset($case);
    ob_start();
    ?>
    <div class="concept-banner"><div><span class="concept-icon">CASE</span><p><strong>Cases are individual incidents.</strong> Use them to investigate one event involving one or more players, collect evidence, coordinate staff and record the outcome.</p></div><a href="index.php?page=documents">Documentation is the reusable staff knowledge base →</a></div>
    <?php if (can('cases.create')): ?>
    <article class="panel"><div class="panel-header"><div><h2>Open a case</h2><span class="cell-sub">Search and select every player involved. The first selection becomes the primary subject.</span></div></div><div class="panel-body"><form class="split-form" method="post" action="index.php?page=cases" enctype="multipart/form-data">
        <?= csrfField() ?><input type="hidden" name="action" value="create-case"><input type="hidden" name="return_page" value="cases">
        <div class="field span-2 player-picker" data-player-picker data-mode="multiple"><label for="case-player-search">Players involved</label><input id="case-player-search" type="search" data-player-query placeholder="Start typing a username…" autocomplete="off"><div class="player-results" data-player-results hidden></div><div class="selected-players" data-player-selected></div><input type="hidden" name="case_players_json" value="[]" data-player-value><span class="field-hint">Select a result to add it. Player name, UUID, platform and skin are filled automatically.</span></div>
        <div class="field"><label for="title">Case title</label><input id="title" name="title" required></div>
        <div class="field"><label for="priority">Priority</label><select id="priority" name="priority"><option value="normal">Normal</option><option value="low">Low</option><option value="high">High</option><option value="critical">Critical</option></select></div>
        <div class="field span-2"><label for="description">Description</label><textarea id="description" name="description" required></textarea></div>
        <div class="field span-2"><label for="case-attachments">Initial evidence files (optional)</label><input id="case-attachments" type="file" name="attachments[]" multiple accept="image/*,video/*,audio/*,.pdf,.txt,.log,.json,.zip"><span class="field-hint">Paste screenshots with Ctrl+V, drag files in, or browse. Staff HQ adds no artificial size or file-count cap; available webspace and host limits still apply.</span></div>
        <div class="span-2"><button class="button" type="submit">Create case</button></div>
    </form></div></article>
    <?php endif; ?>
    <article class="panel"><div class="panel-header"><h2>All cases</h2><span class="badge"><?= count($cases) ?> shown</span></div><div class="panel-body flush"><?php renderCasesTable($cases); ?></div></article>
    <?php
    renderAppPage('Cases', 'cases', (string) ob_get_clean());
}

function showCaseDetail(int $caseId): void
{
    $statement = db()->prepare('SELECT c.*, creator.display_name AS creator_name, assignee.display_name AS assigned_name FROM cases c JOIN users creator ON creator.id = c.created_by LEFT JOIN users assignee ON assignee.id = c.assigned_user_id WHERE c.id = ?');
    $statement->execute([$caseId]);
    $case = $statement->fetch();
    if (!$case) {
        renderErrorPage('Case not found', 'The requested case does not exist.');
        return;
    }
    $events = db()->prepare('SELECT ce.*, u.display_name, u.minecraft_uuid, u.minecraft_username FROM case_events ce LEFT JOIN users u ON u.id = ce.actor_user_id WHERE ce.case_id = ? ORDER BY ce.created_at DESC');
    $events->execute([$caseId]);
    $evidence = db()->prepare('SELECT e.*, u.display_name FROM evidence e JOIN users u ON u.id = e.added_by WHERE e.case_id = ? ORDER BY e.created_at DESC');
    $evidence->execute([$caseId]);
    $punishments = db()->prepare('SELECT p.*, u.display_name, u.minecraft_uuid, u.minecraft_username FROM punishments p LEFT JOIN users u ON u.id = p.issued_by WHERE p.case_id = ? ORDER BY p.issued_at DESC');
    $punishments->execute([$caseId]);
    $staff = db()->query('SELECT id, display_name, role FROM users WHERE active = 1 ORDER BY display_name')->fetchAll();
    $subjects = casePlayers($caseId);
    if ($subjects === []) {
        $subjects[] = ['id' => 0, 'player_name' => $case['player_name'], 'player_uuid' => $case['player_uuid'], 'primary_subject' => 1];
    }
    $attachments = attachmentsFor('case', $caseId);
    $eventRows = $events->fetchAll();
    $evidenceRows = $evidence->fetchAll();
    $punishmentRows = $punishments->fetchAll();
    ob_start();
    ?>
    <div class="button-row page-actions"><a class="button secondary small" href="index.php?page=cases">← All cases</a><span class="badge <?= e($case['status']) ?>"><?= e($case['status']) ?></span><span class="badge <?= e($case['priority']) ?>"><?= e($case['priority']) ?></span><span class="badge"><?= count($subjects) ?> player<?= count($subjects) === 1 ? '' : 's' ?></span></div>
    <section class="content-grid">
        <div>
            <article class="panel">
                <div class="panel-header"><div><span class="eyebrow"><?= e($case['case_number']) ?></span><h2><?= e($case['title']) ?></h2></div></div>
                <div class="panel-body">
                    <h3 class="section-label">Players involved</h3>
                    <div class="subject-list">
                        <?php foreach ($subjects as $subject): ?>
                            <div class="subject-row">
                                <?= renderPlayerIdentity((string) $subject['player_name'], (string) ($subject['player_uuid'] ?? ''), (bool) $subject['primary_subject'] ? 'Primary subject' : 'Case subject') ?>
                                <?php if ((bool) $subject['primary_subject']): ?><span class="badge">Primary</span><?php endif; ?>
                                <?php if (can('cases.manage') && count($subjects) > 1 && (int) $subject['id'] > 0): ?>
                                    <form method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="remove-case-player"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="case_player_id" value="<?= (int) $subject['id'] ?>"><input type="hidden" name="return_page" value="cases"><button class="button danger tiny" data-confirm="Remove this player from the case?" type="submit">Remove</button></form>
                                <?php endif; ?>
                            </div>
                        <?php endforeach; ?>
                    </div>
                    <?php if (can('cases.manage')): ?>
                        <details class="inline-details"><summary>Add more players</summary>
                            <form class="form-stack" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>">
                                <?= csrfField() ?><input type="hidden" name="action" value="add-case-players"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases">
                                <div class="field player-picker" data-player-picker data-mode="multiple"><label>Search known players</label><input type="search" data-player-query placeholder="Start typing a username…" autocomplete="off"><div class="player-results" data-player-results hidden></div><div class="selected-players" data-player-selected></div><input type="hidden" name="additional_players_json" value="[]" data-player-value></div>
                                <button class="button secondary small" type="submit">Add selected players</button>
                            </form>
                        </details>
                    <?php endif; ?>
                    <div class="definition-list case-meta"><dt>Created by</dt><dd><?= e($case['creator_name']) ?></dd><dt>Assigned to</dt><dd><?= e($case['assigned_name'] ?: 'Unassigned') ?></dd><dt>Opened</dt><dd><?= e(displayDate($case['created_at'])) ?></dd></div>
                    <div class="case-description"><?= nl2br(e($case['description'])) ?></div>
                </div>
            </article>

            <article class="panel"><div class="panel-header"><div><h2>Evidence files</h2><span class="cell-sub">Screenshots, video, audio, PDFs, logs and archives</span></div><span class="badge"><?= count($attachments) ?></span></div><div class="panel-body">
                <?php renderAttachmentGallery($attachments, can('cases.manage'), 'cases', ['case_id' => $caseId]); ?>
                <?php if (can('cases.manage')): ?><form class="upload-form" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>" enctype="multipart/form-data"><?= csrfField() ?><input type="hidden" name="action" value="upload-attachment"><input type="hidden" name="owner_type" value="case"><input type="hidden" name="owner_id" value="<?= $caseId ?>"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases"><div class="field"><label>Upload evidence</label><input type="file" name="attachments[]" multiple required accept="image/*,video/*,audio/*,.pdf,.txt,.log,.json,.zip"><span class="field-hint">Paste screenshots with Ctrl+V or add any number of supported files. Staff HQ does not impose its own upload-size cap.</span></div><button class="button secondary" type="submit">Upload files</button></form><?php endif; ?>
            </div></article>

            <article class="panel"><div class="panel-header"><h2>Evidence links</h2><span class="badge"><?= count($evidenceRows) ?></span></div><div class="panel-body">
                <?php if ($evidenceRows === []): ?><div class="empty-state compact"><strong>No external links attached</strong></div><?php else: ?><div class="timeline"><?php foreach ($evidenceRows as $item): ?><div class="timeline-item evidence-row"><div><strong><a href="<?= e($item['url']) ?>" target="_blank" rel="noopener noreferrer"><?= e($item['title']) ?></a></strong><time><?= e($item['display_name']) ?> · <?= e(displayDate($item['created_at'])) ?></time><?php if ($item['description']): ?><p><?= e($item['description']) ?></p><?php endif; ?></div><?php if (can('cases.manage')): ?><form method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="delete-evidence"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="evidence_id" value="<?= (int) $item['id'] ?>"><input type="hidden" name="return_page" value="cases"><button class="button danger tiny" data-confirm="Delete this evidence link?" type="submit">Delete</button></form><?php endif; ?></div><?php endforeach; ?></div><?php endif; ?>
                <?php if (can('cases.manage')): ?><details class="inline-details"><summary>Add an external evidence link</summary><form class="form-stack" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="add-evidence"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases"><div class="split-form"><div class="field"><label>Evidence title</label><input name="title" required></div><div class="field"><label>HTTP(S) URL</label><input type="url" name="url" required></div><div class="field span-2"><label>Description</label><textarea name="description"></textarea></div></div><button class="button secondary" type="submit">Attach link</button></form></details><?php endif; ?>
            </div></article>

            <article class="panel"><div class="panel-header"><h2>Case timeline</h2><span class="badge"><?= count($eventRows) ?></span></div><div class="panel-body"><div class="timeline"><?php if ($eventRows === []): ?><div class="empty-state compact"><strong>No timeline entries yet</strong></div><?php endif; ?><?php foreach ($eventRows as $event): ?><div class="timeline-item"><strong><?= e(ucwords(str_replace('_', ' ', (string) $event['event_type']))) ?> · <?= e($event['display_name'] ?: 'System') ?></strong><time><?= e(displayDate($event['created_at'])) ?></time><?php if ($event['body']): ?><p><?= e(!empty((json_decode((string)($event['metadata_json']??''),true)?:[])['private']) && !can('messages.private') ? '[Private message evidence: additional permission required]' : $event['body']) ?></p><?php endif; ?></div><?php endforeach; ?></div>
                <?php if (can('cases.create')): ?><form class="form-stack" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="add-case-note"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases"><div class="field"><label for="body">Add internal note</label><textarea id="body" name="body" required></textarea></div><button class="button secondary" type="submit">Add note</button></form><?php endif; ?>
            </div></article>
        </div>
        <aside>
            <?php if (can('cases.manage')): ?><article class="panel"><div class="panel-header"><h2>Case controls</h2></div><div class="panel-body"><form class="form-stack" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="update-case"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases"><div class="field"><label>Status</label><select name="status"><?php foreach (['open','in_progress','pending','resolved','closed'] as $value): ?><option value="<?= $value ?>" <?= $case['status'] === $value ? 'selected' : '' ?>><?= e(ucwords(str_replace('_',' ',$value))) ?></option><?php endforeach; ?></select></div><div class="field"><label>Priority</label><select name="priority"><?php foreach (['low','normal','high','critical'] as $value): ?><option value="<?= $value ?>" <?= $case['priority'] === $value ? 'selected' : '' ?>><?= e(ucfirst($value)) ?></option><?php endforeach; ?></select></div><div class="field"><label>Assigned staff member</label><select name="assigned_user_id"><option value="0">Unassigned</option><?php foreach ($staff as $member): ?><option value="<?= (int) $member['id'] ?>" <?= (int) $case['assigned_user_id'] === (int) $member['id'] ? 'selected' : '' ?>><?= e($member['display_name']) ?></option><?php endforeach; ?></select></div><button class="button" type="submit">Update case</button></form></div></article><?php endif; ?>
            <article class="panel"><div class="panel-header"><h2>Recorded actions</h2><span class="badge"><?= count($punishmentRows) ?></span></div><div class="panel-body"><?php if ($punishmentRows === []): ?><div class="empty-state compact"><strong>No actions recorded</strong></div><?php else: ?><div class="timeline"><?php foreach ($punishmentRows as $item): ?><div class="timeline-item"><span class="timeline-player"><?= renderPlayerIdentity((string) $item['player_name'], (string) ($item['player_uuid'] ?? ''), ucfirst((string) $item['punishment_type']), true) ?></span><time><?= e($item['display_name'] ?: $item['issued_by_label'] ?: 'System') ?> · <?= e(displayDate($item['issued_at'])) ?></time><p><?= e($item['reason']) ?></p></div><?php endforeach; ?></div><?php endif; ?>
            <?php if (can('punishments.create')): ?><form class="form-stack" method="post" action="index.php?page=cases&case_id=<?= $caseId ?>"><?= csrfField() ?><input type="hidden" name="action" value="add-punishment"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="player_name" value="<?= e($case['player_name']) ?>"><input type="hidden" name="player_uuid" value="<?= e($case['player_uuid']) ?>"><input type="hidden" name="return_page" value="cases"><div class="field"><label>Player</label><select name="case_player_id" required><?php foreach ($subjects as $subject): ?><option value="<?= (int) $subject['id'] ?>"><?= e($subject['player_name']) ?><?= (bool) $subject['primary_subject'] ? ' · primary' : '' ?></option><?php endforeach; ?></select></div><div class="field"><label>Action</label><select name="punishment_type"><option value="warning">Warning</option><option value="kick">Kick</option><option value="mute">Mute</option><option value="tempban">Temporary ban</option><option value="ban">Permanent ban</option></select></div><div class="field"><label>Reason</label><textarea name="reason" maxlength="1000" required></textarea></div><div class="field"><label>Duration in seconds</label><input type="number" min="1" name="duration_seconds" placeholder="Required for a temporary ban"></div><?php if (can('punishments.execute')): ?><label class="check-row"><input type="checkbox" name="execute_on_server" value="1" checked> Execute through the SMP connector</label><?php endif; ?><button class="button pink" type="submit"><?= can('punishments.execute') ? 'Queue action on SMP' : 'Record action' ?></button></form><?php endif; ?></div></article>
            <?php $current = currentUser(); if (can('cases.manage') && in_array((string) ($current['role'] ?? ''), ['owner','admin'], true)): ?><article class="panel danger-zone"><div class="panel-header"><h2>Danger zone</h2></div><div class="panel-body"><p class="subtle">Permanently deletes this case, its timeline, links and uploaded files.</p><form method="post" action="index.php?page=cases"><?= csrfField() ?><input type="hidden" name="action" value="delete-case"><input type="hidden" name="case_id" value="<?= $caseId ?>"><input type="hidden" name="return_page" value="cases"><button class="button danger" data-confirm="Permanently delete <?= e($case['case_number']) ?> and all of its evidence?" type="submit">Delete case</button></form></div></article><?php endif; ?>
        </aside>
    </section>
    <?php
    renderAppPage($case['case_number'], 'cases', (string) ob_get_clean());
}

function showPunishments(): void
{
    requirePermission('punishments.view');
    $query = trim((string) ($_GET['q'] ?? ''));
    $type = (string) ($_GET['type'] ?? 'all');
    $source = (string) ($_GET['source'] ?? 'all');
    $execution = (string) ($_GET['execution'] ?? 'all');
    $where = [];
    $parameters = [];
    if ($query !== '') {
        $where[] = '(p.player_name LIKE ? OR p.reason LIKE ? OR p.issued_by_label LIKE ? OR u.display_name LIKE ? OR c.case_number LIKE ?)';
        $needle = '%' . $query . '%';
        array_push($parameters, $needle, $needle, $needle, $needle, $needle);
    }
    if (in_array($type, ['warning','kick','mute','tempban','ban'], true)) {
        $where[] = 'p.punishment_type = ?';
        $parameters[] = $type;
    }
    if ($source !== 'all' && preg_match('/^[A-Za-z0-9_.-]{1,32}$/', $source)) {
        $where[] = 'p.source = ?';
        $parameters[] = $source;
    }
    if (in_array($execution, ['recorded','pending','claimed','succeeded','failed'], true)) {
        $where[] = 'p.execution_status = ?';
        $parameters[] = $execution;
    }
    $sql = 'SELECT p.*, u.id AS issuer_id, u.display_name, u.minecraft_uuid, u.minecraft_username, c.case_number
            FROM punishments p LEFT JOIN users u ON u.id = p.issued_by LEFT JOIN cases c ON c.id = p.case_id';
    if ($where !== []) $sql .= ' WHERE ' . implode(' AND ', $where);
    $sql .= ' ORDER BY p.issued_at DESC LIMIT 300';
    $statement = db()->prepare($sql);
    $statement->execute($parameters);
    $rows = $statement->fetchAll();
    $sources = db()->query("SELECT DISTINCT source FROM punishments WHERE source <> '' ORDER BY source")->fetchAll(PDO::FETCH_COLUMN);
    ob_start();
    ?>
    <form class="toolbar filter-toolbar" method="get"><input type="hidden" name="page" value="punishments"><div class="field"><label for="punishment-search">Search history</label><input id="punishment-search" name="q" value="<?= e($query) ?>" placeholder="Player, reason, staff or case"></div><div class="field compact-field"><label>Action</label><select name="type"><option value="all">All actions</option><?php foreach (['warning','kick','mute','tempban','ban'] as $option): ?><option value="<?= $option ?>" <?= $type === $option ? 'selected' : '' ?>><?= e(ucfirst($option)) ?></option><?php endforeach; ?></select></div><div class="field compact-field"><label>Source</label><select name="source"><option value="all">All sources</option><?php foreach ($sources as $option): ?><option value="<?= e($option) ?>" <?= $source === $option ? 'selected' : '' ?>><?= e($option) ?></option><?php endforeach; ?></select></div><div class="field compact-field"><label>Execution</label><select name="execution"><option value="all">All states</option><?php foreach (['recorded','pending','claimed','succeeded','failed'] as $option): ?><option value="<?= $option ?>" <?= $execution === $option ? 'selected' : '' ?>><?= e(ucfirst($option)) ?></option><?php endforeach; ?></select></div><button class="button secondary" type="submit">Search</button><?php if ($query !== '' || $type !== 'all' || $source !== 'all' || $execution !== 'all'): ?><a class="button secondary" href="index.php?page=punishments">Clear</a><?php endif; ?></form>
    <article class="panel"><div class="panel-header"><div><h2>Punishment history</h2><span class="cell-sub">Search similar reasons, players, staff decisions and case references.</span></div><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Player</th><th>Action</th><th>Reason</th><th>Execution</th><th>Case</th><th>Issued by</th><th>Date</th></tr></thead><tbody><?php if ($rows === []): ?><tr><td colspan="7"><div class="empty-state"><strong>No matching punishments</strong><p>Try a broader search or clear the filters.</p></div></td></tr><?php endif; ?><?php foreach ($rows as $row): ?><tr><td><?= renderPlayerIdentity((string) $row['player_name'], (string) ($row['player_uuid'] ?? ''), null, true) ?></td><td><span class="badge"><?= e($row['punishment_type']) ?></span></td><td class="wrapping-cell"><?= e($row['reason']) ?></td><td><span class="badge <?= e($row['execution_status'] ?? 'recorded') ?>"><?= e($row['execution_status'] ?? 'recorded') ?></span></td><td><?= $row['case_number'] ? '<a href="index.php?page=cases&case_id=' . (int) $row['case_id'] . '">' . e($row['case_number']) . '</a>' : '—' ?></td><td><?= $row['issuer_id'] ? linkedStaffIdentity($row) : e($row['issued_by_label'] ?: 'System') ?></td><td><?= e(displayDate($row['issued_at'])) ?></td></tr><?php endforeach; ?></tbody></table></div></div></article>
    <?php
    renderAppPage('Punishments', 'punishments', (string) ob_get_clean());
}

function showDocuments(): void
{
    requirePermission('documents.view');
    hqStaffGuideId();
    $query = trim((string) ($_GET['q'] ?? ''));
    if ($query !== '') {
        $statement = db()->prepare('SELECT d.*, u.display_name FROM documents d JOIN users u ON u.id = d.updated_by WHERE d.title LIKE ? OR d.category LIKE ? OR d.body LIKE ? ORDER BY d.updated_at DESC');
        $statement->execute(['%' . $query . '%', '%' . $query . '%', '%' . $query . '%']);
        $documents = $statement->fetchAll();
    } else {
        $documents = db()->query('SELECT d.*, u.display_name FROM documents d JOIN users u ON u.id = d.updated_by ORDER BY d.updated_at DESC')->fetchAll();
    }
    $selectedId = (int) ($_GET['document_id'] ?? 0);
    $creating = isset($_GET['new']) && can('documents.manage');
    $importing = isset($_GET['import']) && can('documents.manage');
    $editing = isset($_GET['edit']) && can('documents.manage');
    $selected = null;
    foreach ($documents as $document) {
        if ((int) $document['id'] === $selectedId) {
            $selected = $document;
            break;
        }
    }
    if ($selectedId > 0 && !$selected) {
        renderErrorPage('Document not found', 'The requested staff document does not exist or no longer matches the current search.');
        return;
    }
    $attachments = $selected ? attachmentsFor('document', (int) $selected['id']) : [];
    ob_start();
    ?>
    <div class="page-intro"><div><strong>Staff knowledge base</strong><p>Browse existing policies and procedures first. The editor only opens when you explicitly create or edit a document.</p></div><div class="button-row"><?php if (can('documents.manage')): ?><a class="button" href="index.php?page=documents&new=1">New document</a><a class="button secondary" href="index.php?page=documents&import=1">Import DOCX / Google Docs</a><?php endif; ?></div></div>

    <?php if ($creating): ?>
        <article class="panel editor-panel"><div class="panel-header"><div><h2>Create document</h2><span class="cell-sub">Start a reusable guide, rule or staff procedure.</span></div><a class="button secondary small" href="index.php?page=documents">Cancel</a></div><div class="panel-body"><form class="form-stack" method="post" action="index.php?page=documents" enctype="multipart/form-data"><?= csrfField() ?><input type="hidden" name="action" value="create-document"><input type="hidden" name="return_page" value="documents"><div class="split-form"><div class="field"><label>Title</label><input name="title" required autofocus></div><div class="field"><label>Category</label><input name="category" value="Moderation" required></div><div class="field span-2"><label>Content</label><textarea class="document-editor" name="body" required></textarea></div><div class="field span-2"><label>Files (optional)</label><input type="file" name="attachments[]" multiple accept="image/*,video/*,audio/*,.pdf,.txt,.log,.json,.zip,.docx"><span class="field-hint">Screenshots can be pasted. Large files are automatically uploaded in small chunks, so the host's 2 MB request limit no longer applies.</span></div></div><button class="button" type="submit">Create document</button></form></div></article>
    <?php elseif ($importing): ?>
        <article class="panel editor-panel"><div class="panel-header"><div><h2>Import a document</h2><span class="cell-sub">Converts DOCX headings, paragraphs and lists into editable Staff HQ text.</span></div><a class="button secondary small" href="index.php?page=documents">Cancel</a></div><div class="panel-body"><form class="form-stack" method="post" action="index.php?page=documents&import=1" enctype="multipart/form-data"><?= csrfField() ?><input type="hidden" name="action" value="import-document"><input type="hidden" name="return_page" value="documents"><div class="split-form"><div class="field"><label>Title (optional)</label><input name="title" placeholder="Uses the file name when empty"></div><div class="field"><label>Category</label><input name="category" value="Imported" required></div><div class="field span-2"><label>Word, Markdown or text file</label><input type="file" name="import_file" accept=".docx,.md,.txt"></div><div class="import-divider span-2"><span>or</span></div><div class="field span-2"><label>Google Docs URL</label><input type="url" name="google_docs_url" placeholder="https://docs.google.com/document/d/…"><span class="field-hint">The Google document must permit link access. Private account authentication is not stored by Staff HQ.</span></div></div><button class="button" type="submit">Convert and import</button></form></div></article>
    <?php elseif ($selected): ?>
        <section class="content-grid"><div>
            <article class="panel"><div class="panel-header"><div><h2><?= e($selected['title']) ?></h2><span class="cell-sub">Updated by <?= e($selected['display_name']) ?> · <?= e(displayDate($selected['updated_at'])) ?></span></div><div class="button-row"><span class="badge"><?= e($selected['category']) ?></span><?php if (can('documents.manage')): ?><a class="button secondary small" href="index.php?page=documents&document_id=<?= (int) $selected['id'] ?>&edit=1">Edit</a><?php endif; ?></div></div><div class="panel-body"><div class="document-body"><?= nl2br(e($selected['body'])) ?></div></div></article>
            <article class="panel"><div class="panel-header"><div><h2>Files and media</h2><span class="cell-sub">Images, clips, PDFs, logs and supporting material</span></div><span class="badge"><?= count($attachments) ?></span></div><div class="panel-body"><?php renderAttachmentGallery($attachments, can('documents.manage'), 'documents', ['document_id' => (int) $selected['id']]); ?><?php if (can('documents.manage')): ?><form class="upload-form" method="post" action="index.php?page=documents&document_id=<?= (int) $selected['id'] ?>" enctype="multipart/form-data"><?= csrfField() ?><input type="hidden" name="action" value="upload-attachment"><input type="hidden" name="owner_type" value="document"><input type="hidden" name="owner_id" value="<?= (int) $selected['id'] ?>"><input type="hidden" name="document_id" value="<?= (int) $selected['id'] ?>"><input type="hidden" name="return_page" value="documents"><div class="field"><label>Add files</label><input type="file" name="attachments[]" multiple required accept="image/*,video/*,audio/*,.pdf,.txt,.log,.json,.zip,.docx"><span class="field-hint">Paste screenshots, drop files or browse. Uploads are chunked automatically.</span></div><button class="button secondary" type="submit">Upload files</button></form><?php endif; ?></div></article>
            <?php if ($editing): ?><article class="panel editor-panel"><div class="panel-header"><h2>Edit document</h2><a class="button secondary small" href="index.php?page=documents&document_id=<?= (int) $selected['id'] ?>">Close editor</a></div><div class="panel-body"><form class="form-stack" method="post" action="index.php?page=documents&document_id=<?= (int) $selected['id'] ?>&edit=1" enctype="multipart/form-data"><?= csrfField() ?><input type="hidden" name="action" value="update-document"><input type="hidden" name="document_id" value="<?= (int) $selected['id'] ?>"><input type="hidden" name="return_page" value="documents"><div class="split-form"><div class="field"><label>Title</label><input name="title" value="<?= e($selected['title']) ?>" required></div><div class="field"><label>Category</label><input name="category" value="<?= e($selected['category']) ?>" required></div><div class="field span-2"><label>Content</label><textarea class="document-editor" name="body" required><?= e($selected['body']) ?></textarea></div><div class="field span-2"><label>Attach more files (optional)</label><input type="file" name="attachments[]" multiple accept="image/*,video/*,audio/*,.pdf,.txt,.log,.json,.zip,.docx"></div></div><button class="button" type="submit">Save changes</button></form><form class="danger-action" method="post" action="index.php?page=documents"><?= csrfField() ?><input type="hidden" name="action" value="delete-document"><input type="hidden" name="document_id" value="<?= (int) $selected['id'] ?>"><input type="hidden" name="return_page" value="documents"><button class="button danger" data-confirm="Permanently delete this document and all uploaded files?" type="submit">Delete document</button></form></div></article><?php endif; ?>
        </div><aside><article class="panel sticky-panel"><div class="panel-header"><h2>All documents</h2><a class="button secondary tiny" href="index.php?page=documents">Browse</a></div><div class="document-nav"><?php foreach (array_slice($documents, 0, 20) as $document): ?><a class="<?= (int) $document['id'] === $selectedId ? 'active' : '' ?>" href="index.php?page=documents&document_id=<?= (int) $document['id'] ?>"><strong><?= e($document['title']) ?></strong><span><?= e($document['category']) ?> · <?= e(displayDate($document['updated_at'])) ?></span></a><?php endforeach; ?></div></article></aside></section>
    <?php else: ?>
        <form class="toolbar" method="get"><input type="hidden" name="page" value="documents"><div class="field"><label for="document-search">Search documentation</label><input id="document-search" name="q" value="<?= e($query) ?>" placeholder="Title, category or content"></div><button class="button secondary" type="submit">Search</button><?php if ($query !== ''): ?><a class="button secondary" href="index.php?page=documents">Clear</a><?php endif; ?></form>
        <div class="document-card-grid"><?php if ($documents === []): ?><article class="panel"><div class="empty-state"><strong><?= $query !== '' ? 'No matching documents' : 'No documentation yet' ?></strong><p><?= $query !== '' ? 'Try another search.' : 'Create or import the first staff guide.' ?></p></div></article><?php endif; ?><?php foreach ($documents as $document): ?><a class="document-card" href="index.php?page=documents&document_id=<?= (int) $document['id'] ?>"><span class="badge"><?= e($document['category']) ?></span><h2><?= e($document['title']) ?></h2><p><?= e(mb_substr(trim(preg_replace('/\s+/', ' ', (string) $document['body']) ?? ''), 0, 170)) ?><?= mb_strlen((string) $document['body']) > 170 ? '…' : '' ?></p><small>Updated by <?= e($document['display_name']) ?> · <?= e(displayDate($document['updated_at'])) ?></small></a><?php endforeach; ?></div>
    <?php endif; ?>
    <?php
    renderAppPage('Documentation', ($_GET['page'] ?? '') === 'staff-guide' ? 'staff-guide' : 'documents', (string) ob_get_clean());
}

function showTeam(): void
{
    $actor = requirePermission('team.view');
    header('Cache-Control: no-store');
    $users = db()->query("SELECT * FROM users ORDER BY FIELD(role, 'owner', 'admin', 'moderator', 'helper'), display_name")->fetchAll();
    $oneTime = $_SESSION['one_time_staff_password'] ?? null;
    unset($_SESSION['one_time_staff_password']);
    $newStaffForm = $_SESSION['new_staff_form'] ?? [];
    unset($_SESSION['new_staff_form']);
    $canManage = can('team.manage');
    $canCreate = (string) $actor['role'] === 'owner';
    $creating = $canCreate && (isset($_GET['new']) || $newStaffForm !== []);
    $newStaffPlayer = !empty($newStaffForm['minecraft_username']) && !empty($newStaffForm['minecraft_uuid'])
        ? ['username' => $newStaffForm['minecraft_username'], 'uuid' => $newStaffForm['minecraft_uuid'], 'platform' => 'known player'] : null;
    ob_start();
    ?>
    <?php if (is_array($oneTime) && $canManage): ?><div class="notice notice-warning"><strong>One-time password for <?= e($oneTime['email']) ?></strong><div class="secret-box"><code id="staff-password"><?= e($oneTime['password']) ?></code></div><button class="button secondary small" type="button" data-copy="#staff-password">Copy password</button><p>Copy it now; it is only shown once. The staff member must change it and set up two-factor authentication before entering Staff HQ.</p></div><?php endif; ?>
    <div class="page-intro"><div><strong>Staff identities</strong><p>Link each website account to its Minecraft account. Linked staff actions can then show the correct player name and skin instead of only an email or “System”.</p></div><?php if ($canCreate && !$creating): ?><a class="button" href="index.php?page=team&new=1">Add staff</a><?php endif; ?></div>
    <?php if ($creating): ?>
        <article class="panel editor-panel">
            <div class="panel-header"><div><h2>Add staff</h2><span class="cell-sub">Create an account and choose its staff role.</span></div><a class="button secondary small" href="index.php?page=team">Cancel</a></div>
            <div class="panel-body">
                <form class="form-stack" method="post" action="index.php?page=team">
                    <?= csrfField() ?>
                    <input type="hidden" name="action" value="create-user">
                    <input type="hidden" name="return_page" value="team">
                    <div class="split-form">
                        <div class="field"><label for="new-staff-email">Email address</label><input id="new-staff-email" type="email" name="email" maxlength="190" value="<?= e($newStaffForm['email'] ?? '') ?>" autocomplete="off" required autofocus><span class="field-hint">Used to sign in to Staff HQ.</span></div>
                        <div class="field"><label for="new-staff-name">Display name</label><input id="new-staff-name" name="display_name" maxlength="80" value="<?= e($newStaffForm['display_name'] ?? '') ?>" autocomplete="off" required></div>
                        <div class="field"><label for="new-staff-role">Role</label><select id="new-staff-role" name="role"><?php foreach (['owner', 'admin', 'moderator', 'helper'] as $role): if ((string) $actor['role'] !== 'owner' && $role === 'owner') continue; ?><option value="<?= $role ?>" <?= ($newStaffForm['role'] ?? 'helper') === $role ? 'selected' : '' ?>><?= e(roleLabel($role)) ?></option><?php endforeach; ?></select></div>
                        <div class="field player-picker" data-player-picker data-mode="single" data-initial="<?= e(jsonEncode($newStaffPlayer)) ?>">
                            <label for="new-staff-player">Minecraft account (optional)</label><input id="new-staff-player" type="search" data-player-query placeholder="Search a player…" autocomplete="off">
                            <div class="player-results" data-player-results hidden></div><div class="selected-players" data-player-selected></div>
                            <input type="hidden" name="minecraft_username" value="<?= e($newStaffPlayer['username'] ?? '') ?>" data-player-name><input type="hidden" name="minecraft_uuid" value="<?= e($newStaffPlayer['uuid'] ?? '') ?>" data-player-uuid>
                            <span class="field-hint">Select a known player, or link the account later using Manage.</span>
                        </div>
                    </div>
                    <p class="subtle">A temporary password is generated automatically and shown once after creation. The first login requires a new password and two-factor authentication.</p>
                    <div class="button-row"><button class="button" type="submit">Create account</button></div>
                </form>
            </div>
        </article>
    <?php endif; ?>
    <article class="panel"><div class="panel-header"><h2>Staff accounts</h2><span class="badge"><?= count($users) ?> accounts</span></div><div class="panel-body flush"><div class="table-wrap"><table class="staff-table"><thead><tr><th>Staff member</th><th>Minecraft account</th><th>Role</th><th>Security</th><th>Last login</th><th>Controls</th></tr></thead><tbody><?php foreach ($users as $staff): ?><tr><td><span class="cell-main"><?= e($staff['display_name']) ?></span><span class="cell-sub"><?= e($staff['email']) ?></span></td><td><?= linkedStaffIdentity($staff) ?></td><td><span class="badge <?= e($staff['role']) ?>"><?= e(roleLabel($staff['role'])) ?></span></td><td><?= (bool) $staff['totp_enabled'] ? '<span class="badge online">2FA on</span>' : '<span class="badge open">2FA required</span>' ?> <?= (bool) $staff['active'] ? '<span class="badge online">Active</span>' : '<span class="badge">Disabled</span>' ?></td><td><?= e(displayDate($staff['last_login_at'])) ?></td><td><?php if (can('team.manage') && ((string) $actor['role'] === 'owner' || (string) $staff['role'] !== 'owner')): ?><details class="manage-details"><summary class="button secondary small">Manage</summary><form class="form-stack staff-manage-form" method="post" action="index.php?page=team"><?= csrfField() ?><input type="hidden" name="action" value="update-user"><input type="hidden" name="user_id" value="<?= (int) $staff['id'] ?>"><input type="hidden" name="return_page" value="team"><div class="field"><label>Role</label><select name="role"><?php foreach (['owner','admin','moderator','helper'] as $role): if ((string) $actor['role'] !== 'owner' && $role === 'owner') continue; ?><option value="<?= $role ?>" <?= $staff['role'] === $role ? 'selected' : '' ?>><?= e(roleLabel($role)) ?></option><?php endforeach; ?></select></div><div class="field player-picker" data-player-picker data-mode="single" data-initial="<?= e(jsonEncode(!empty($staff['minecraft_username']) ? ['username' => $staff['minecraft_username'], 'uuid' => $staff['minecraft_uuid'], 'platform' => 'linked', 'avatar' => avatarUrl((string) $staff['minecraft_uuid'], (string) $staff['minecraft_username'], 48)] : null)) ?>"><label>Linked Minecraft account</label><input type="search" data-player-query placeholder="Search a player…" autocomplete="off"><div class="player-results" data-player-results hidden></div><div class="selected-players" data-player-selected></div><input type="hidden" name="minecraft_username" value="<?= e($staff['minecraft_username'] ?? '') ?>" data-player-name><input type="hidden" name="minecraft_uuid" value="<?= e($staff['minecraft_uuid'] ?? '') ?>" data-player-uuid><span class="field-hint">Choose a known player to link their UUID and skin.</span></div><label class="check-row"><input type="checkbox" name="unlink_minecraft"> Unlink current Minecraft account</label><label class="check-row"><input type="checkbox" name="active" <?= (bool) $staff['active'] ? 'checked' : '' ?>> Active account</label><button class="button small" type="submit">Save account</button></form><?php if ((int) $staff['id'] !== (int) $actor['id']): ?><form method="post" action="index.php?page=team" class="security-reset"><?= csrfField() ?><input type="hidden" name="action" value="reset-user-security"><input type="hidden" name="user_id" value="<?= (int) $staff['id'] ?>"><input type="hidden" name="return_page" value="team"><button class="button danger small" data-confirm="Reset this account's password and two-factor authentication?" type="submit">Reset password and 2FA</button></form><?php endif; ?></details><?php else: ?>—<?php endif; ?></td></tr><?php endforeach; ?></tbody></table></div></div></article>
    <?php if ((string) $actor['role'] === 'owner'): $permissionRole = (string) ($_GET['permissions_role'] ?? 'moderator'); if (!in_array($permissionRole, ['admin','moderator','helper'], true)) $permissionRole = 'moderator'; $rolePermissions = permissionsForRole($permissionRole); $groups = []; foreach (permissionCatalog() as $permission => $details) $groups[$details['group']][$permission] = $details['label']; ?>
    <article class="panel"><div class="panel-header"><div><h2>Role visibility and permissions</h2><span class="cell-sub">Controls which pages each rank can see and which actions it may perform. Owner access is always unrestricted.</span></div><div class="segmented"><?php foreach (['admin','moderator','helper'] as $role): ?><a class="<?= $permissionRole === $role ? 'active' : '' ?>" href="index.php?page=team&permissions_role=<?= $role ?>"><?= e(roleLabel($role)) ?></a><?php endforeach; ?></div></div><div class="panel-body"><form class="form-stack" method="post" action="index.php?page=team&permissions_role=<?= e($permissionRole) ?>"><?= csrfField() ?><input type="hidden" name="action" value="update-role-permissions"><input type="hidden" name="role" value="<?= e($permissionRole) ?>"><input type="hidden" name="return_page" value="team"><div class="permission-grid"><?php foreach ($groups as $group => $permissions): ?><fieldset><legend><?= e($group) ?></legend><?php foreach ($permissions as $permission => $label): ?><label class="check-row"><input type="checkbox" name="permissions[]" value="<?= e($permission) ?>" <?= in_array($permission, $rolePermissions, true) ? 'checked' : '' ?>><span><strong><?= e($label) ?></strong><small><?= e($permission) ?></small></span></label><?php endforeach; ?></fieldset><?php endforeach; ?></div><button class="button" type="submit">Save <?= e(roleLabel($permissionRole)) ?> permissions</button></form></div></article>
    <?php endif; ?>
    <?php
    renderAppPage('Staff Team', 'team', (string) ob_get_clean());
}

function activityRange(): int
{
    $range = (int) ($_GET['range'] ?? 7);
    return in_array($range, [1, 3, 7, 14, 30], true) ? $range : 7;
}

function showCommandLogs(): void
{
    requirePermission('commands.view');
    $query = trim((string) ($_GET['q'] ?? ''));
    $server = trim((string) ($_GET['server'] ?? ''));
    $command = strtolower(trim((string) ($_GET['command'] ?? '')));
    $range = activityRange();
    $where = ["a.action LIKE 'command.%'", "a.created_at >= DATE_SUB(NOW(), INTERVAL $range DAY)"];
    $params = [];
    if ($query !== '') {
        $where[] = '(a.actor_label LIKE ? OR a.command_text LIKE ? OR a.target_id LIKE ? OR a.actor_minecraft_uuid = ? OR a.action = ?)';
        $uuid = normalizeUuid($query) ?? $query;
        $queryLabel = strtolower(preg_replace('/[^a-z0-9_-]/', '', ltrim($query, '/')) ?? '');
        array_push($params, '%' . $query . '%', '%' . ltrim($query, '/') . '%', '%' . $query . '%', $uuid, 'command.' . $queryLabel);
    }
    if ($server !== '') {
        $where[] = 'a.source_server = ?';
        $params[] = $server;
    }
    if ($command !== '') {
        $where[] = 'a.action = ?';
        $params[] = 'command.' . preg_replace('/[^a-z0-9_-]/', '', $command);
    }
    $statement = db()->prepare(
        'SELECT a.*, u.id AS linked_user_id, u.display_name, u.minecraft_uuid, u.minecraft_username
         FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_user_id
         WHERE ' . implode(' AND ', $where) . ' ORDER BY a.created_at DESC LIMIT 500'
    );
    $statement->execute($params);
    $rows = $statement->fetchAll();
    $servers = db()->query("SELECT DISTINCT source_server FROM audit_logs WHERE action LIKE 'command.%' AND source_server IS NOT NULL AND source_server <> '' ORDER BY source_server")->fetchAll(PDO::FETCH_COLUMN);
    $commands = db()->query("SELECT action, COUNT(*) total FROM audit_logs WHERE action LIKE 'command.%' AND created_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) GROUP BY action ORDER BY total DESC, action LIMIT 100")->fetchAll();
    ob_start();
    ?>
    <div class="page-intro"><div><strong>Player command history</strong><p>Every player command from SMP and Hub is searchable here. Message bodies are kept in the separate Message Log; credentials and account secrets are always redacted.</p></div><span class="badge">30-day retention</span></div>
<form class="toolbar activity-toolbar" method="get"><input type="hidden" name="page" value="commands"><div class="field"><label for="command-q">Player, target or command text</label><input id="command-q" name="q" value="<?= e($query) ?>" placeholder="e.g. Valentlnes, /shop or a UUID"></div><div class="field"><label for="command-filter">Command</label><select id="command-filter" name="command"><option value="">All commands</option><?php foreach ($commands as $item): $label = substr((string) $item['action'], 8); ?><option value="<?= e($label) ?>" <?= $command === $label ? 'selected' : '' ?>>/<?= e($label) ?> (<?= (int) $item['total'] ?>)</option><?php endforeach; ?></select></div><div class="field"><label for="command-server">Server</label><select id="command-server" name="server"><option value="">SMP + Hub</option><?php foreach ($servers as $item): ?><option value="<?= e($item) ?>" <?= $server === $item ? 'selected' : '' ?>><?= e(strtoupper((string) $item)) ?></option><?php endforeach; ?></select></div><div class="field"><label for="command-range">Period</label><select id="command-range" name="range"><?php foreach ([1,3,7,14,30] as $days): ?><option value="<?= $days ?>" <?= $range === $days ? 'selected' : '' ?>>Last <?= $days ?> day<?= $days === 1 ? '' : 's' ?></option><?php endforeach; ?></select></div><button class="button secondary" type="submit">Search</button><a class="button secondary" href="index.php?page=commands">Clear</a></form>
    <article class="panel"><div class="panel-header"><h2>Command Log</h2><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table class="activity-table"><thead><tr><th>Date</th><th>Player</th><th>Command</th><th>Target</th><th>Server</th></tr></thead><tbody><?php if ($rows === []): ?><tr><td colspan="5"><div class="empty-state"><strong>No matching commands</strong><p>Try a broader search or period.</p></div></td></tr><?php endif; ?><?php foreach ($rows as $row): ?><tr><td><?= e(displayDate($row['created_at'])) ?></td><td><div class="identity-with-badge"><?= renderPlayerIdentity((string) ($row['actor_label'] ?: 'Unknown'), (string) ($row['actor_minecraft_uuid'] ?? ''), $row['linked_user_id'] ? (string) $row['display_name'] : 'Player', true) ?><?php if ($row['linked_user_id']): ?><span class="badge online">Linked staff</span><?php endif; ?></div></td><td class="wrapping-cell"><code><?= e($row['command_text'] ?: substr((string) $row['action'], 8)) ?></code></td><td><?= e($row['target_id'] ?: '—') ?></td><td><span class="badge"><?= e(strtoupper((string) ($row['source_server'] ?: 'network'))) ?></span></td></tr><?php endforeach; ?></tbody></table></div></div></article>
    <?php
    renderAppPage('Command Log', 'commands', (string) ob_get_clean());
}

function showMessageLogs(): void
{
    requirePermission('messages.view');
    $query = trim((string) ($_GET['q'] ?? ''));
    $server = trim((string) ($_GET['server'] ?? 'smp'));
    $channel = strtolower(trim((string) ($_GET['channel'] ?? '')));
    $range = activityRange();
    $where = ["m.sent_at >= DATE_SUB(NOW(), INTERVAL $range DAY)"];
    $params = [];
    if (!can('messages.private')) $where[] = "m.channel_name <> 'private'";
    if ($query !== '') {
        $where[] = '(m.player_name LIKE ? OR m.recipient_name LIKE ? OR m.message_text LIKE ? OR m.player_uuid = ? OR m.recipient_uuid = ?)';
        $uuid = normalizeUuid($query) ?? $query;
        array_push($params, '%' . $query . '%', '%' . $query . '%', '%' . $query . '%', $uuid, $uuid);
    }
    if ($server !== '') {
        $where[] = 'm.source_server = ?';
        $params[] = $server;
    }
    if ($channel !== '') {
        $where[] = 'm.channel_name = ?';
        $params[] = preg_replace('/[^a-z0-9_.-]/', '', $channel);
    }
    $statement = db()->prepare(
        'SELECT m.*, u.id AS linked_user_id, u.display_name AS linked_display_name
         FROM player_messages m LEFT JOIN users u ON u.active = 1 AND u.minecraft_uuid = m.player_uuid
         WHERE ' . implode(' AND ', $where) . ' ORDER BY m.sent_at DESC LIMIT 500'
    );
    $statement->execute($params);
    $rows = $statement->fetchAll();
    $servers = db()->query("SELECT DISTINCT source_server FROM player_messages WHERE source_server IS NOT NULL AND source_server <> '' ORDER BY source_server")->fetchAll(PDO::FETCH_COLUMN);
    $channels = db()->query("SELECT channel_name, COUNT(*) total FROM player_messages WHERE sent_at >= DATE_SUB(NOW(), INTERVAL 30 DAY) GROUP BY channel_name ORDER BY total DESC, channel_name")->fetchAll();
    ob_start();
    obsServerTabs('messages',$server);
    ?>
    <div class="page-intro"><div><strong>Public and private message history</strong><p>Search Global, Local, Town, Nation, Alliance and private messages when investigating a report or reconstructing a bug discussion.</p></div><span class="badge">30-day retention</span></div>
<form class="toolbar activity-toolbar" method="get"><input type="hidden" name="page" value="messages"><div class="field"><label for="message-q">Player, recipient or message text</label><input id="message-q" name="q" value="<?= e($query) ?>" placeholder="Search a name, UUID or phrase"></div><div class="field"><label for="message-channel">Channel</label><select id="message-channel" name="channel"><option value="">All channels</option><?php foreach ($channels as $item): ?><option value="<?= e($item['channel_name']) ?>" <?= $channel === $item['channel_name'] ? 'selected' : '' ?>><?= e(ucfirst((string) $item['channel_name'])) ?> (<?= (int) $item['total'] ?>)</option><?php endforeach; ?></select></div><div class="field"><label for="message-server">Server</label><select id="message-server" name="server"><option value="">SMP + Hub</option><?php foreach ($servers as $item): ?><option value="<?= e($item) ?>" <?= $server === $item ? 'selected' : '' ?>><?= e(strtoupper((string) $item)) ?></option><?php endforeach; ?></select></div><div class="field"><label for="message-range">Period</label><select id="message-range" name="range"><?php foreach ([1,3,7,14,30] as $days): ?><option value="<?= $days ?>" <?= $range === $days ? 'selected' : '' ?>>Last <?= $days ?> day<?= $days === 1 ? '' : 's' ?></option><?php endforeach; ?></select></div><button class="button secondary" type="submit">Search</button><a class="button secondary" href="index.php?page=messages">Clear</a></form>
    <article class="panel"><div class="panel-header"><h2>Message Log</h2><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table class="activity-table message-table"><thead><tr><th>Date</th><th>Sender</th><th>Channel</th><th>Recipient</th><th>Message</th><th>Server</th></tr></thead><tbody><?php if ($rows === []): ?><tr><td colspan="6"><div class="empty-state"><strong>No matching messages</strong><p>Try a broader search or period.</p></div></td></tr><?php endif; ?><?php foreach ($rows as $row): ?><tr><td><?= e(displayDate($row['sent_at'])) ?></td><td><div class="identity-with-badge"><?= renderPlayerIdentity((string) $row['player_name'], (string) $row['player_uuid'], $row['linked_user_id'] ? (string) $row['linked_display_name'] : 'Player', true) ?><?php if ($row['linked_user_id']): ?><span class="badge online">Linked staff</span><?php endif; ?></div></td><td><span class="badge <?= $row['channel_name'] === 'private' ? 'high' : '' ?>"><?= e(ucfirst((string) $row['channel_name'])) ?></span></td><td><?= $row['recipient_name'] ? renderPlayerIdentity((string) $row['recipient_name'], (string) ($row['recipient_uuid'] ?? ''), 'Recipient', true) : '—' ?></td><td class="wrapping-cell message-content"><?= nl2br(e($row['message_text'])) ?></td><td><span class="badge"><?= e(strtoupper((string) ($row['source_server'] ?: 'network'))) ?></span></td></tr><?php endforeach; ?></tbody></table></div></div></article>
    <?php
    renderAppPage('Message Log', 'messages', (string) ob_get_clean());
}

function showAudit(): void
{
    requirePermission('audit.view');
    $rows = db()->query("SELECT a.*, u.id AS linked_user_id, u.display_name, u.minecraft_uuid, u.minecraft_username FROM audit_logs a LEFT JOIN users u ON u.id = a.actor_user_id WHERE a.action <> 'command.pv' AND (a.action NOT LIKE 'command.%' OR a.actor_user_id IS NOT NULL) ORDER BY a.created_at DESC LIMIT 300")->fetchAll();
    ob_start();
    ?>
    <div class="page-intro"><div><strong>Staff and website actions</strong><p>Website changes, moderation activity and commands from Minecraft accounts linked to a Staff HQ account appear here. All player commands remain available separately in Command Log.</p></div></div>
    <article class="panel"><div class="panel-header"><h2>Staff activity trail</h2><span class="badge"><?= count($rows) ?> shown</span></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Date</th><th>Actor</th><th>Action</th><th>Target</th><th>Details</th></tr></thead><tbody><?php if ($rows === []): ?><tr><td colspan="5"><div class="empty-state"><strong>No audit entries yet</strong></div></td></tr><?php endif; ?><?php foreach ($rows as $row): ?><tr><td><?= e(displayDate($row['created_at'])) ?></td><td><?= $row['linked_user_id'] ? linkedStaffIdentity($row) : e($row['actor_label'] ?: 'System') ?></td><td><span class="badge"><?= e($row['action']) ?></span></td><td><?= e(trim(($row['target_type'] ?: '') . ' ' . ($row['target_id'] ?: '')) ?: '—') ?></td><td class="wrapping-cell"><?php if ($row['command_text']): ?><code><?= e($row['command_text']) ?></code><?php else: ?><span class="cell-sub"><?= e($row['metadata_json'] ?: '—') ?></span><?php endif; ?></td></tr><?php endforeach; ?></tbody></table></div></div></article>
    <?php
    renderAppPage('Staff Audit', 'audit', (string) ob_get_clean());
}

function showSettings(): void
{
    requirePermission('api.manage');
    $clients = db()->query('SELECT * FROM api_clients ORDER BY created_at')->fetchAll();
    $newToken = $_SESSION['new_api_token'] ?? null;
    unset($_SESSION['new_api_token']);
    $storageFree = @disk_free_space(APP_ROOT . '/storage');
    $uploadLimit = (string) ini_get('upload_max_filesize');
    $postLimit = (string) ini_get('post_max_size');
    $fileCountLimit = (string) ini_get('max_file_uploads');
    ob_start();
    ?>
    <?php if (is_string($newToken)): ?><div class="notice notice-warning"><strong>Copy the new API token now</strong><div class="secret-box"><code id="new-api-token"><?= e($newToken) ?></code></div><button class="button secondary small" type="button" data-copy="#new-api-token">Copy token</button></div><?php endif; ?>
    <section class="stats-grid secondary-stats system-stats"><article class="stat-card"><span>Staff HQ attachment cap</span><strong>None</strong><small>MIME/security and free-space checks remain active</small></article><article class="stat-card"><span>Chunk transport</span><strong>512 KB</strong><small>Bypasses the host's <?= e($uploadLimit ?: 'unknown') ?> per-request limit</small></article><article class="stat-card"><span>PHP POST / simultaneous files</span><strong><?= e(($postLimit === '0' ? 'Unlimited' : ($postLimit ?: 'Unknown')) . ' / ' . ($fileCountLimit ?: 'Unknown')) ?></strong><small>Does not limit the chunked uploader</small></article><article class="stat-card"><span>Free evidence storage</span><strong><?= $storageFree !== false ? e(formatBytes((int) $storageFree)) : 'Unknown' ?></strong><small>Files use this webspace, not MySQL</small></article></section>
    <section class="content-grid"><div><article class="panel"><div class="panel-header"><h2>Network API clients</h2></div><div class="panel-body flush"><div class="table-wrap"><table><thead><tr><th>Name</th><th>Token prefix</th><th>Status</th><th>Last seen</th><th>Rotate</th></tr></thead><tbody><?php foreach ($clients as $client): ?><tr><td><?= e($client['client_name']) ?></td><td><code><?= e($client['token_prefix']) ?>…</code></td><td><?= (bool) $client['active'] ? '<span class="badge online">Active</span>' : '<span class="badge">Disabled</span>' ?></td><td><?= e(displayDate($client['last_seen_at'])) ?></td><td><form method="post" action="index.php?page=settings"><?= csrfField() ?><input type="hidden" name="action" value="rotate-api-token"><input type="hidden" name="client_id" value="<?= (int) $client['id'] ?>"><input type="hidden" name="return_page" value="settings"><button class="button danger small" data-confirm="The existing server token will stop working immediately. Continue?" type="submit">Rotate</button></form></td></tr><?php endforeach; ?></tbody></table></div></div></article></div><aside><article class="panel"><div class="panel-header"><h2>API endpoint</h2></div><div class="panel-body"><div class="code-block">POST <?= e(config('app_url')) ?>/api.php
Authorization: Bearer YOUR_TOKEN
Content-Type: application/json</div><p class="subtle">Accepted events: player updates, anti-cheat alerts, punishments, staff actions, player commands, public/private messages, server health and plugin inventory. Command and message records expire automatically after 30 days. Remote actions are restricted to typed warning, kick, mute and ban requests.</p></div></article></aside></section>
    <?php
    renderAppPage('System', 'settings', (string) ob_get_clean());
}

function renderAlertsTable(array $alerts, bool $controls): void
{
    $columnCount = $controls && can('alerts.manage') ? 8 : 7;
    ?>
    <div class="table-wrap alerts-wrap"><table class="alerts-table"><thead><tr><th>Player</th><th>Check</th><th>Severity</th><th>VL</th><th>Server</th><th>Status</th><th>Time</th><?= $controls && can('alerts.manage') ? '<th>Review</th>' : '' ?></tr></thead><tbody>
    <?php if ($alerts === []): ?><tr><td colspan="<?= $columnCount ?>"><div class="empty-state"><strong>No alerts received</strong><p>The server connector will send anti-cheat events here.</p></div></td></tr><?php endif; ?>
    <?php foreach ($alerts as $alert): ?><tr><td><?= renderPlayerIdentity((string) $alert['player_name'], (string) ($alert['player_uuid'] ?? ''), null, true) ?></td><td class="alert-message"><span class="cell-main"><?= e($alert['check_name']) ?></span><span class="cell-sub"><?= e($alert['message'] ?: 'No additional details') ?></span></td><td><span class="badge <?= e($alert['severity']) ?>"><?= e($alert['severity']) ?></span></td><td><?= e($alert['violation_level'] ?? '—') ?></td><td><?= e($alert['server_name'] ?: '—') ?></td><td><span class="badge <?= e($alert['status']) ?>"><?= e($alert['status']) ?></span></td><td><?= e(displayDate($alert['occurred_at'])) ?></td><?php if ($controls && can('alerts.manage')): ?><td class="review-cell"><form class="review-control" method="post" action="index.php?page=alerts"><?= csrfField() ?><input type="hidden" name="action" value="update-alert"><input type="hidden" name="alert_id" value="<?= (int) $alert['id'] ?>"><input type="hidden" name="return_page" value="alerts"><select name="status" aria-label="Alert status"><?php foreach (['open','reviewed','escalated','dismissed'] as $reviewStatus): ?><option value="<?= $reviewStatus ?>" <?= $alert['status'] === $reviewStatus ? 'selected' : '' ?>><?= e(ucfirst($reviewStatus)) ?></option><?php endforeach; ?></select><button class="button secondary small" type="submit">Save</button></form></td><?php endif; ?></tr><?php endforeach; ?>
    </tbody></table></div>
    <?php
}

function renderCasesTable(array $cases): void
{
    ?>
    <div class="table-wrap"><table><thead><tr><th>Case</th><th>Players</th><th>Priority</th><th>Status</th><th>Assigned</th><th>Updated</th></tr></thead><tbody><?php if ($cases === []): ?><tr><td colspan="6"><div class="empty-state"><strong>No cases yet</strong><p>Create a case when an incident needs investigation and evidence.</p></div></td></tr><?php endif; ?><?php foreach ($cases as $case): $subjects = !empty($case['subjects']) ? $case['subjects'] : [['player_name' => $case['player_name'], 'player_uuid' => $case['player_uuid']]]; ?><tr><td><a class="cell-main" href="index.php?page=cases&case_id=<?= (int) $case['id'] ?>"><?= e($case['case_number']) ?></a><span class="cell-sub"><?= e($case['title']) ?></span></td><td><div class="case-subject-stack"><?php foreach (array_slice($subjects, 0, 3) as $subject): ?><?= renderPlayerIdentity((string) $subject['player_name'], (string) ($subject['player_uuid'] ?? ''), null, true) ?><?php endforeach; ?><?php if (count($subjects) > 3): ?><span class="badge">+<?= count($subjects) - 3 ?> more</span><?php endif; ?></div></td><td><span class="badge <?= e($case['priority']) ?>"><?= e($case['priority']) ?></span></td><td><span class="badge <?= e($case['status']) ?>"><?= e($case['status']) ?></span></td><td><?= e($case['assigned_name'] ?: 'Unassigned') ?></td><td><?= e(displayDate($case['updated_at'])) ?></td></tr><?php endforeach; ?></tbody></table></div>
    <?php
}
