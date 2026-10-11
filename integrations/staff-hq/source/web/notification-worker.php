<?php
declare(strict_types=1);

// Plesk: Run a PHP script, once a minute. This endpoint is never public.
if(PHP_SAPI!=='cli'){
    http_response_code(404);
    exit;
}
if(!is_file(__DIR__.'/config/config.php')){
    fwrite(STDERR,"Staff HQ configuration is missing.\n");
    exit(1);
}
try{
    require_once __DIR__.'/app/bootstrap.php';
    $result=notificationWorker(db());
    echo 'Staff HQ notification worker: '.jsonEncode($result)."\n";
}catch(Throwable){
    // Exceptions may contain infrastructure secrets; only expose a fixed diagnostic.
    fwrite(STDERR,"Staff HQ notification worker failed. Check PHP/database availability.\n");
    exit(1);
}
