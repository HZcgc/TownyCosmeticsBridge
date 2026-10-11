<?php
declare(strict_types=1);

// Authenticated external scheduler entry point. No browser session or public cron URL key.
header('Content-Type: application/json; charset=utf-8');
header('Cache-Control: no-store, private');
header('X-Robots-Tag: noindex, nofollow, noarchive');
header('X-Content-Type-Options: nosniff');
$submitted=(string)($_SERVER['HTTP_X_TR_CRON_KEY']??'');
if(!preg_match('/^tr_cron_[a-f0-9]{64}$/D',$submitted)||!is_file(__DIR__.'/config/config.php')){
    http_response_code(404);echo '{"error":"not_found"}';exit;
}
try{
    require_once __DIR__.'/app/bootstrap.php';
    if(!notificationCronAuthorized(notificationSettings(),$submitted)){
        http_response_code(404);echo '{"error":"not_found"}';exit;
    }
    if(!isHttps()){
        http_response_code(400);echo '{"error":"https_required"}';exit;
    }
    if(($_SERVER['REQUEST_METHOD']??'')!=='POST'){
        header('Allow: POST');http_response_code(405);echo '{"error":"post_required"}';exit;
    }
    if((int)($_SERVER['CONTENT_LENGTH']??0)>1024){
        http_response_code(413);echo '{"error":"request_too_large"}';exit;
    }
    ignore_user_abort(true);
    @set_time_limit(25);
    $result=notificationWorker(db());
    echo jsonEncode(['ok'=>true,'result'=>$result]);
}catch(Throwable){
    http_response_code(503);
    echo '{"error":"worker_unavailable"}';
}
