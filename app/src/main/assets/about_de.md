© 2020  Michael Huebler and other contributors.
Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, dass es Dir von Nutzen sein wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie ALLGEMEINER GEBRAUCHSTAUGLICHKEIT oder der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details findest Du in der GNU General Public License.

![alternative text](file:///android_asset/icon_large.png)

# Funktionen
Diese App hilft Dir, Warnungen der offiziellen Corona-Warn-App besser zu verstehen und einzuordnen.

**ACHTUNG:** DIE APP BENÖTIGT ROOT RECHTE. Ohne Root Rechte ist nur ein Demo-Modus möglich, der nicht auf Deine Risiko-Begegnungen zugreifen kann.

### Was die App macht:
1. Die App liest die von Deinem Gerät aufgezeichneten Rolling Proximity IDs aus der Exposure Notifications Datenbank aus (das geht nur mit Root Rechten, weshalb die offizielle Corona-Warn-App diese Details nicht anzeigen kann).
2. Die App lädt die Positiv-Schlüssel (Diagnose-Schlüssel, Diagnosis Keys) vom offiziellen Corona-Warn-Server. Dabei lädt sie die täglich veröffentlichen Schlüssel der letzten Tage und die stündlich veröffentlichten Schlüssel des heutigen Tages. Daher werden möglicherweise andere Informationen als in der offiziellen Corona-Warn-App angezeigt. 
3. Die App gleicht beides ab, um Übereinstimmungen (Risiko-Begegnungen) zu finden.

Wenn Risiko-Begegnungen gefunden wurden, zeigt sie die Details dazu an: 
Zu welchen Uhrzeiten und mit welcher Funk-Dämpfung (entspricht grob dem Abstand) haben die Begegnungen stattgefunden, und welche Übertragungsrisiko-Stufe hatte die Begegnung.

### Was die App nicht macht:
- Die App verarbeitet keine personenbezogenen Daten.
- Die App greift auf das Internet nur zum o.g. Zweck 2 zu, d.h. sie lädt nur Daten vom offiziellen Corona-Warn-Server, und sie sendet keine Daten an andere Server.
- Die App zeigt keine Werbung.

**Hinweis:**  Diese App wurde bisher nur auf wenigen Geräten getestet, es ist daher möglich, dass sie nicht funktioniert und/oder falsche Ergebnisse anzeigt. 

# Open Source
Der Source Code der App ist unter https://github.com/mh-/corona-warn-companion-android veröffentlicht, Du kannst also den Quelltext prüfen, Dir die App auch selbst bauen und gerne auch an Verbesserungen mitwirken.

# Weiteres
- Die App dient rein privaten Zwecken, mit ihr wird kein geschäftlicher Zweck verfolgt.
- Die App ist kein "Hacker-Tool". Sie liest lediglich Daten aus dem Speicher Deines eigenen Geräts aus, die dort unverschlüsselt abgelegt sind.
