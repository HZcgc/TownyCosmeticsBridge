# Staff HQ 5.2.1 / Connector 1.5.1 – HTTP 422 und 50.000er Queue

Dieses Paket aktualisiert das bestehende HQ. Journey beta.9 bleibt installiert. Es gibt keinen Reset, keine neue Perk-Berechnung und keine neuen Minecraft-Permissions.

## 1. Website zuerst aktualisieren

HQ-Dateien und Datenbank sichern. Danach **den Inhalt von `WEBSPACE-UPDATE/`** in das tatsächlich von `staff.townyreborn.com` verwendete Web-Hauptverzeichnis kopieren. Nicht den Ordner `WEBSPACE-UPDATE` selbst als Unterordner hochladen. Alle enthaltenen PHP-Dateien ersetzen, einschließlich `api.php` und `app/game-insights.php`.

Die bestehenden `config/config.php`, `config/notifications.php` und der gesamte Ordner `storage/` bleiben erhalten. Das ZIP enthält diese privaten Daten nicht. Die vorhandene Datenbank weiterverwenden; Schema 11 und bestehende Gruppenrechte bleiben erhalten. Der vorhandene Cronjob bleibt unverändert erforderlich.

Danach im Browser `https://staff.townyreborn.com/api.php` öffnen. Die JSON-Antwort muss enthalten:

```json
{
  "status": "ok",
  "version": 9,
  "release": "5.2.1",
  "journey": true,
  "capabilities": { "journey": true }
}
```

Weitere Felder sind normal. Diese Prüfung benötigt keinen API-Token. Steht dort eine ältere Version, das Web-Hauptverzeichnis und die ersetzte `api.php` prüfen. Bei korrekt hochgeladenen Dateien gegebenenfalls PHP-FPM/OPcache über das Hosting neu starten lassen. Ein Browser-Hard-Refresh leert keinen PHP-OPcache. Bei `journey: false` sind die geladenen Website-Dateien nicht vollständig auf diesem Stand.

## 2. Connector ersetzen

SMP stoppen, die bisherige `TownyRebornStaffConnector-*.jar` entfernen und **genau eine** `TownyRebornStaffConnector-1.5.1.jar` einsetzen. Beim Connector-ZIP den Inhalt von `SERVER-UPDATE/` über das Serververzeichnis kopieren. Auf dem Hub ebenso ausschließlich die Connector-JAR ersetzen; dort ist kein Journey-Plugin erforderlich. Vollständig starten, keinen PlugMan-/Bukkit-Reload verwenden. Velocity-/Proxy-Plugins bleiben separat.

Den bestehenden Connector-Datenordner behalten: `config.yml`, Token/Endpoint, `pending-events.jsonl`, `insights-outbox.json`, Telemetrie-Outbox, Cursor, Ausführungsbelege, `rejected-events.jsonl` und Reparatur-Checkpoints nicht löschen. Auch die Journey-Daten und deren Audit-/Migrationsjournale bleiben bestehen. Für diesen Hotfix weder die Journey-JAR noch deren Config ersetzen.

## 3. Queue kontrollieren

Die effektive Mindestkapazität beträgt jetzt **50.000 Ereignisse**, selbst wenn in einer alten Connector-Config noch `maximum-queued-events: 5000` steht. Die Config wird nicht umgeschrieben. Bereits gespeicherte Einträge und ihre Event-IDs bleiben erhalten; auch eine schon größere Datei wird beim Laden nicht gekürzt.

Optional kann unter `portal:` ausdrücklich `maximum-queued-events: 50000` eingetragen werden. Höhere konfigurierte Kapazitäten bis 500.000 werden berücksichtigt. Keine mitgelieferte Muster-Config über den echten API-Token kopieren.

`/trstaff status` zeigt nun beispielsweise `queued: 5000 / 50000` und nach einer Antwort `HQ API: 9`. Bei erfolgreicher Übertragung sinkt der Rückstau. Eine API-Gesundheitsprüfung allein bestätigt keine Ereignisse und leert die Queue nicht.

## Was wurde behoben?

Connector 1.5.0 behandelte einen fehlenden `X-TR-API-Version`-Header wie eine alte API und sperrte einen ganzen Fehler-Batch, sobald irgendwo Journey enthalten war. 1.5.1 berücksichtigt die JSON-Versions-/Fähigkeitsangaben und bei Bedarf die echte GET-Gesundheitsantwort. Entscheidend ist der vom HQ identifizierte abgewiesene Eintrag.

Unbekannte/fehlende Journey-Unterstützung und widersprüchlich abgewiesene gültige Journey-Snapshots bleiben erhalten. Tatsächlich ungültige Daten werden nur mit passender Event-ID, Event-Typ und Fehlerposition einzeln archiviert. Gültige Einträge werden dabei nicht als ungültig verworfen. API 9 liefert Version, Release und tatsächlich geladene Journey-Unterstützung auch bei HTTP 422 im JSON, unabhängig von benutzerdefinierten Response-Headern.

Der Screenshot allein beweist nicht, welche PHP-Version der Webhost tatsächlich ausgeliefert hat. Der obige öffentliche JSON-Check macht den installierten Stand überprüfbar. Echte Hosting-/Datenbankfehler können weiterhin auftreten; Warteschlangen nicht zur Fehlerbehebung löschen.
