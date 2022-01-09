© 2020-2022  Michael Huebler and other contributors.
Die Veröffentlichung dieses Programms erfolgt in der Hoffnung, dass es Dir von Nutzen sein wird, aber OHNE IRGENDEINE GARANTIE, sogar ohne die implizite Garantie ALLGEMEINER GEBRAUCHSTAUGLICHKEIT oder der VERWENDBARKEIT FÜR EINEN BESTIMMTEN ZWECK. Details findest Du in der GNU General Public License.

# Funktionen
Diese App hilft Dir, Warnungen der offiziellen Corona-Warn-App besser zu verstehen und einzuordnen.

**ACHTUNG:** Um die Daten der Warn-App zu lesen, benötigt die App Root Rechte. Ohne Root Rechte kann sie nur zusammen mit der RaMBLE App oder mit der CCTG App / microG verwendet werden.

### Was die App macht:
1. Die App liest die von Deinem Gerät aufgezeichneten Rolling Proximity IDs aus der Exposure Notifications Datenbank aus (das geht nur mit Root Rechten, weshalb offizielle Exposure Notifications Apps wie die Corona-Warn-App diese Details nicht anzeigen können).

   ![-Beispiel Erfasste Begegnungen-](file:///android_asset/rpis_de.png)
   Alternativ kann die App auch eine aus der RaMBLE App oder aus microG exportierte Datenbank lesen (auch ohne Root Rechte)..

2. Die App lädt die Positiv-Schlüssel (Diagnose-Schlüssel, Diagnosis Keys) vom offiziellen Corona-Warn-Server und Servern in anderen Ländern, wie von Dir ausgewählt. Dabei lädt sie z.B. für Deutschland die täglich veröffentlichen Schlüssel der letzten Tage und die stündlich veröffentlichten Schlüssel des heutigen Tages.

   ![-Beispiel Positiv-Schlüssel-](file:///android_asset/dks_de.png)

3. Die App gleicht beides ab, um Übereinstimmungen (Risiko-Begegnungen) zu finden.

   ![-Beispiel Treffer-](file:///android_asset/matches_de.png)

Wenn Risiko-Begegnungen gefunden wurden, zeigt sie die Details dazu an: 
Zu welchen Uhrzeiten und mit welcher Funk-Dämpfung (entspricht grob dem Abstand) haben die Begegnungen stattgefunden, und welche Übertragungsrisiko-Stufe hatte die Begegnung.
Im RaMBLE-Modus kann auch die Position der Begegnung angezeigt werden, denn die RaMBLE App speichert zu jedem Eintrag eine Geoposition.
![-Beispiel Details-](file:///android_asset/details_de.png)

Dabei entspricht 1 einem niedrigen und 8 einem hohen Übertragungsrisiko.

### Was die App nicht macht:
- Die App verarbeitet keine personenbezogenen Daten.
- Die App greift auf das Internet nur zum o.g. Zweck 2 zu, d.h. sie lädt nur Daten von offiziellen Corona-Warn-Servern, und sie sendet keine Daten an andere Server (außer wenn Du im RaMBLE-Modus die Kartenanzeige aktivierst, dann lädt sie eine Karte von OpenStreetMap Servern).
- Die App zeigt keine Werbung.

# Open Source
Der Source Code der App ist unter https://github.com/mh-/corona-warn-companion-android veröffentlicht, Du kannst also den Quelltext prüfen, Dir die App auch selbst bauen und gerne auch an Verbesserungen mitwirken.

# Unterstützte Länder
Vom deutschen Warn-App-Server:
🇩🇪 Deutschland, 🇧🇪 Belgien, 🇩🇰 Dänemark, 🇪🇪 Estland, 🇫🇮 Finnland, 🇮🇪 Irland, 🇮🇹 Italien, 🇭🇷 Kroatien, 🇱🇻 Lettland, 🇱🇹 Litauen, 🇲🇹 Malta, 🇳🇱 Niederlande, 🇳🇴 Norwegen, 🇦🇹 Österreich, 🇵🇱 Polen, 🇸🇮 Slowenien, 🇨🇭 Schweiz, 🇪🇸 Spanien, 🇨🇾 Zypern

Von Servern der jeweiligen Länder:
- Österreich
- Belgien
- Kanada
- Niederlande
- Polen
- Schweiz
- England und Wales

Die Länder können über das Menü oben rechts in der App ausgewählt werden.

Bitte beachte, dass wir hauptsächlich verfolgen, was sich beim deutschen CWA Server tut. Wenn Du Probleme mit dem Download für ein anderes Land hast, melde uns das bitte als ein GitHub Issue.

# Weiteres
- Die App dient rein privaten Zwecken, mit ihr wird kein geschäftlicher Zweck verfolgt.
- Die App ist kein "Hacker-Tool". Sie liest lediglich Daten aus dem Speicher Deines eigenen Geräts aus, die dort ohne zusätzliche Verschlüsselung abgelegt sind.
