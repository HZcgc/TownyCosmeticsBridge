<?php
declare(strict_types=1);
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
$_SERVER['SCRIPT_NAME']='maintenance.php';
require __DIR__.'/app/bootstrap.php';
$deadline=time()+240;$total=0;
do{$changed=obsRetention(db());$total+=$changed;}while($changed>0&&time()<$deadline);
fwrite(STDOUT,"Staff HQ retention completed: $total rows removed or scrubbed. Anonymous statistics and case evidence preserved.\n");
