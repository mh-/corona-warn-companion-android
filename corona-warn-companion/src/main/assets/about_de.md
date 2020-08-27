© 2020  Michael Huebler and other contributors.
Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, dass es Dir von Nutzen sein wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie ALLGEMEINER GEBRAUCHSTAUGLICHKEIT oder der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details findest Du in der GNU General Public License.

# Funktionen
Diese App hilft Dir, Warnungen der offiziellen Corona-Warn-App besser zu verstehen und einzuordnen.

**ACHTUNG:** FÜR VOLLE FUNKTION BENÖTIGT DIE APP ROOT RECHTE. Ohne Root Rechte kann sie nicht auf Deine Risiko-Begegnungen zugreifen, dann gibt es nur drei Funktionen: 1. Testen, wie viele Positiv-Schlüssel vom Server geladen werden können, 2. RaMBLE Daten verwenden und 3. Demo Modus.

### Was die App macht:
1. Die App liest die von Deinem Gerät aufgezeichneten Rolling Proximity IDs aus der Exposure Notifications Datenbank aus (das geht nur mit Root Rechten, weshalb offizielle Exposure Notifications Apps wie die Corona-Warn-App diese Details nicht anzeigen können).
   ![-Beispiel Erfasste Begegnungen-](file:///android_asset/rpis_de.png)
   Alternativ kann die App auch eine aus RaMBLE exportierte Datenbank lesen (auch ohne Root Rechte).
2. Die App lädt die Positiv-Schlüssel (Diagnose-Schlüssel, Diagnosis Keys) vom offiziellen Corona-Warn-Server und Servern in anderen Ländern, wie von Dir ausgewählt. Dabei lädt sie für Deutschland die täglich veröffentlichen Schlüssel der letzten Tage und die stündlich veröffentlichten Schlüssel des heutigen Tages. Daher werden möglicherweise andere Informationen als in der offiziellen Corona-Warn-App angezeigt.
   ![-Beispiel Positiv-Schlüssel-](file:///android_asset/dks_de.png)
3. Die App gleicht beides ab, um Übereinstimmungen (Risiko-Begegnungen) zu finden.
   ![-Beispiel Treffer-](file:///android_asset/matches_de.png)

Wenn Risiko-Begegnungen gefunden wurden, zeigt sie die Details dazu an: 
Zu welchen Uhrzeiten und mit welcher Funk-Dämpfung (entspricht grob dem Abstand) haben die Begegnungen stattgefunden, und welche Übertragungsrisiko-Stufe hatte die Begegnung.
![-Beispiel Details-](file:///android_asset/details_de.png)

Dabei entspricht 1 einem niedrigen und 8 einem hohen Übertragungsrisiko.

### Was die App nicht macht:
- Die App verarbeitet keine personenbezogenen Daten.
- Die App greift auf das Internet nur zum o.g. Zweck 2 zu, d.h. sie lädt nur Daten vom offiziellen Corona-Warn-Server, und sie sendet keine Daten an andere Server.
- Die App zeigt keine Werbung.

**Hinweis:**  Diese App wurde bisher nur auf wenigen Geräten getestet, es ist daher möglich, dass sie nicht funktioniert und/oder falsche Ergebnisse anzeigt. 

# Open Source
Der Source Code der App ist unter https://github.com/mh-/corona-warn-companion-android veröffentlicht, Du kannst also den Quelltext prüfen, Dir die App auch selbst bauen und gerne auch an Verbesserungen mitwirken.

# Unterstützte Länder
- Deutschland
- Österreich
- Polen
- Schweiz

Die Länder können über das Menü oben rechts in der App ausgewählt werden.

Bitte beachte, dass wir hauptsächlich verfolgen, was sich beim deutschen CWA Server tut. Wenn Du Probleme mit dem Download für ein anderes Land hast, melde uns das bitte als ein GitHub Issue.

# Weiteres
- Die App dient rein privaten Zwecken, mit ihr wird kein geschäftlicher Zweck verfolgt.
- Die App ist kein "Hacker-Tool". Sie liest lediglich Daten aus dem Speicher Deines eigenen Geräts aus, die dort ohne zusätzliche Verschlüsselung abgelegt sind.
