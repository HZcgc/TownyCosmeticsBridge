<?php
declare(strict_types=1);

// Deliberately CLI-only. History changes statistics, never balances, keys or grants.
if(PHP_SAPI!=='cli'){http_response_code(404);exit;}
require dirname(__DIR__).'/app/bootstrap.php';
if($argc!==2||!is_readable($argv[1])){fwrite(STDERR,"Usage: php cli/import-game-history.php /private/path/crate-history.jsonl\n");exit(2);}
$handle=fopen($argv[1],'rb');$pdo=db();$lines=0;$inserted=0;
try{
    while(($line=fgets($handle))!==false){
        if(trim($line)==='')continue;
        $row=json_decode($line,true,32,JSON_THROW_ON_ERROR);$lines++;
        if(!is_array($row)||($row['type']??'')!=='game.event'||($row['plugin']??'')!=='ExcellentCrates'||!in_array($row['metric']??'',['crate_openings','crate_reward'],true))throw new RuntimeException('Unsupported history at line '.$lines);
        $server=obsText($row['server']??'smp',64);if(!preg_match('/^[a-z0-9_.-]{1,64}$/',$server))throw new RuntimeException('Invalid server');
        $row['imported']=true;$pdo->beginTransaction();
        $q=$pdo->prepare('SELECT COUNT(*) FROM game_events WHERE event_key=?');$q->execute([hash('sha256',$server.':'.($row['event_id']??''))]);$exists=(bool)$q->fetchColumn();
        if(empty($row['occurred_at']))throw new RuntimeException('Missing historical timestamp');
        $at=(new DateTimeImmutable((string)$row['occurred_at']))->setTimezone(new DateTimeZone((string)config('timezone','Europe/Berlin')))->format('Y-m-d H:i:s');
        gameIngest($pdo,$row,$server,$at);$pdo->commit();if(!$exists)$inserted++;
    }
    echo jsonEncode(['records_read'=>$lines,'new_statistics_records'=>$inserted,'duplicates_skipped'=>$lines-$inserted]).PHP_EOL;
}catch(Throwable $e){if($pdo->inTransaction())$pdo->rollBack();fwrite(STDERR,'History import stopped at line '.$lines.': '.$e->getMessage().PHP_EOL);exit(1);}
finally{fclose($handle);}
