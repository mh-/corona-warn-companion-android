package org.tosl.coronawarncompanion.dkdownload;

// DownloadFileInfo consists of
// - the filename (may later be used to have this file ignored)
// - the last day for which keys are contained in the file - if this cannot be determined, set to 0
public class DownloadFileInfo {
    public String countryCode;
    public String filename;
    public int maxDay = 0;  // unit: days since epoch

    public DownloadFileInfo(String countryCode, String filename, int maxDay) {
        this.countryCode = countryCode;
        this.filename = filename;
        this.maxDay = maxDay;
    }
}