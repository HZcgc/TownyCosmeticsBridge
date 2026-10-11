from pathlib import Path
import importlib.util,unittest,tempfile,json
r=Path(__file__).resolve().parents[1];spec=importlib.util.spec_from_file_location('crate_history',r/'tools/crate_history.py');history=importlib.util.module_from_spec(spec);spec.loader.exec_module(history)
class HistoryTests(unittest.TestCase):
 def test_batch(self):
  line='[2026-10-10 12:31:53] Player: Tester | Crate: vote | location: spawn | maxAllowed:7 | selectedAmount:7 | Rewards Given: '+', '.join(['RewardId[crateId=vote, rewardId=coin]']*7)+' | Rewards Required:7'
  rows=history.events(line,0);self.assertEqual([7,7],[r['value'] for r in rows]);self.assertEqual('2026-10-10T10:31:53Z',rows[0]['occurred_at']);self.assertEqual(rows,history.events(line,0));self.assertNotEqual(rows[0]['event_id'],history.events(line,99)[0]['event_id'])
 def test_old_format(self):
  rows=history.events('[13/09/2026 02:55:12] Tester won 4 Ender Pearls (ID: ender_pearls_4, Weight: 8.0) from Daily Crate (ID: daily)',0);self.assertEqual('daily',rows[0]['dimension']);self.assertEqual(1,rows[0]['value']);self.assertEqual('daily|ender_pearls_4',rows[1]['dimension'])
 def test_partial_unknown_does_not_invent_opening(self):self.assertEqual([],history.events('Random system log',0))
 def test_multiline_item_and_reordered_fields(self):
  line='[2026-10-10 12:31:53] Player: Tester | Crate: daily | itemStack: chest {\n"item":"value"\n} | selectedAmount: 1 | maxAllowed: 1 | Rewards Given: RewardId[crateId=daily, rewardId=stone] | Rewards Required: 1'
  self.assertEqual(1,history.events(line,0)[0]['value'])
 def test_utf8_offsets_and_repeatable_conversion(self):
  with tempfile.TemporaryDirectory() as temp:
   p=Path(temp);(p/'openings.log').write_text('[13/09/2026 02:55:12] Tester won Grün (ID: green, Weight: 8.0) from Daily Crate (ID: daily)\n',encoding='utf-8')
   one=p/'one.jsonl';two=p/'two.jsonl';self.assertEqual(history.convert(p,one),history.convert(p,two));self.assertEqual(one.read_bytes(),two.read_bytes())
if __name__=='__main__':unittest.main()
