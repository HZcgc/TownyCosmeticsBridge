#!/usr/bin/env python3
"""Convert ExcellentCrates 6/7 history to repeat-safe, read-only HQ statistics."""
from pathlib import Path
from datetime import datetime,timezone
from zoneinfo import ZoneInfo
from collections import Counter
import argparse,hashlib,json,re

BATCH=re.compile(r'^\[([0-9-]+ [0-9:]+)] Player: (\S+) \| Crate: (\S+) \|.*?selectedAmount:\s*(\d+) \|.*?Rewards Given: (.*?) \| Rewards Required:\s*(\d+)\s*$',re.DOTALL)
LEGACY=re.compile(r'^\[(\d\d/\d\d/\d{4} \d\d:\d\d:\d\d)] (\S+) won (.*?) \(ID: ([^,]+), Weight: ([^)]+)\) from (.*?) \(ID: ([^)]+)\)\s*$')
REWARDS=re.compile(r'rewardId=([^] ,]+)')

def events(line,offset,server='smp',zone='Europe/Berlin'):
 m=BATCH.match(line);kind='batch'
 if not m:m=LEGACY.match(line);kind='legacy'
 if not m:return []
 if kind=='batch':
  date,player,crate,amount,rewardText,required=m.groups();amount=int(amount);rewards=REWARDS.findall(rewardText)
  stamp=datetime.strptime(date,'%Y-%m-%d %H:%M:%S');prefix='crate-history-'
  details={'selected_amount':amount,'rewards':rewards,'rewards_required':int(required),'source':'confirmed_batch_history'}
 else:
  date,player,rewardName,reward,weight,crateName,crate=m.groups();amount=1;rewards=[reward]
  stamp=datetime.strptime(date,'%d/%m/%Y %H:%M:%S');prefix='crate-legacy-';details={'reward':reward,'reward_name':rewardName,'weight':float(weight),'source':'legacy_opening_history'}
 if not 1<=amount<=1000000:raise ValueError('Invalid batch amount')
 identity=prefix+hashlib.sha256((str(offset)+':'+line).encode()).hexdigest()
 base={'type':'game.event','server':server,'event_id':identity,'plugin':'ExcellentCrates','metric':'crate_openings','dimension':crate,'username':player,'value':amount,'count':1,'details':details,'occurred_at':stamp.replace(tzinfo=ZoneInfo(zone)).astimezone(timezone.utc).isoformat().replace('+00:00','Z'),'imported':True}
 output=[base]
 for reward,count in Counter(rewards).items():
  key=hashlib.sha256((identity+':reward:'+reward).encode()).hexdigest()
  output.append({**base,'event_id':'crate-reward-'+key,'metric':'crate_reward','dimension':crate+'|'+reward,'value':count,'count':count,'details':{'crate':crate,'reward':reward,'source':'imported_reward_history'}})
 return output

def convert(source,output,server='smp',zone='Europe/Berlin'):
 counts={'records':0,'opening_batches':0,'openings':0,'reward_items':0,'unparsed_lines':0};seen=set()
 with output.open('w',encoding='utf-8') as destination:
  for filename in ['openings.log','logs/crate_history.log']:
   file=source/filename
   if not file.is_file():continue
   offset=0
   records=[];pending=b'';start=0
   for raw in file.read_bytes().splitlines(keepends=True):
    if re.match(rb'^\[\d',raw):
     if pending:records.append((pending,start))
     pending=raw;start=offset
    else:pending+=raw
    offset+=len(raw)
   if pending:records.append((pending,start))
   for raw,position in records:
    line=raw.decode('utf-8').replace('\r\n','\n').rstrip('\r\n');rows=events(line,position,server,zone)
    if line and not rows:counts['unparsed_lines']+=1
    for row in rows:
     if row['event_id'] in seen:continue
     seen.add(row['event_id']);destination.write(json.dumps(row,ensure_ascii=False,separators=(',',':'))+'\n');counts['records']+=1
     if row['metric']=='crate_openings':counts['opening_batches']+=1;counts['openings']+=row['value']
     else:counts['reward_items']+=row['value']
 return counts

if __name__=='__main__':
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('source',type=Path);parser.add_argument('output',type=Path);parser.add_argument('--server',default='smp');parser.add_argument('--timezone',default='Europe/Berlin');args=parser.parse_args()
 print(json.dumps(convert(args.source,args.output,args.server,args.timezone),indent=2))
