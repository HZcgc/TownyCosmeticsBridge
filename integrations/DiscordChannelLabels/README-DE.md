# DiscordSRV: sichtbarer Herkunftskanal

Die mitgelieferte messages.yml basiert auf deiner DiscordSRV(4).zip. Nur die beiden Discord-zu-Minecraft-Formate wurden geändert. Nachrichten tragen jetzt DISCORD [#Kanalname], danach Name, Antwortbezug und Nachricht. Das gilt für Nutzer mit und ohne Discord-Rolle und automatisch für künftig verbundene Kanäle.

messages.yml nach plugins/DiscordSRV/messages.yml kopieren und DiscordSRV mit /discord reload neu laden oder den Server regulär starten. Die vorhandene config.yml mit Bot-Token und Kanalzuordnung behalten. TownyChat leitet Staff-/Adminnachrichten weiterhin in die bisher zugeordneten Kanäle; diese Datei ändert nur die sichtbare Herkunftsangabe.

Offizielle Platzhalter: https://docs.discordsrv.com/messages/
