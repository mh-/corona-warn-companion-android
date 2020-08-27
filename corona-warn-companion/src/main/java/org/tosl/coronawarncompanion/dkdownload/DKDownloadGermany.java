package org.tosl.coronawarncompanion.dkdownload;

import android.annotation.SuppressLint;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.StringRequest;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.AsyncSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class DKDownloadGermany implements DKDownloadCountry {

    static class DateURL {
        private final Date date;
        private final URL url;

        public DateURL(Date date, URL url) {
            this.date = date;
            this.url = url;
        }

        public Date getDate() {
            return date;
        }

        public URL getUrl() {
            return url;
        }
    }

    private static final String CWA_URL = "https://svc90.main.px.t-online.de/version/v1/diagnosis-keys/country/DE/date";

    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy-MM-dd");

    private static String[] parseCwsListResponse(String str) {
        String reducedStr = str.replace("\"","");
        reducedStr = reducedStr.replace("[","");
        reducedStr = reducedStr.replace("]","");
        return reducedStr.split(",");
    }

    private static String getStringFromDate(Date date) {
        StringBuffer stringBuffer = new StringBuffer();
        return dateFormatter.format(date, stringBuffer, new FieldPosition(0)).toString();
    }

    private static URL getDailyDKsURLForDate(Date date) {
        URL result = null;
        try {
            result = new URL(CWA_URL+"/"+getStringFromDate(date));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }
        return result;
    }

    private static Single<List<DateURL>> getDailyUrls(RequestQueue queue, Date minDate) {
        Subject<List<DateURL>> dailyUrlSubject = AsyncSubject.create();
        StringRequest stringRequest = new StringRequest(
                Request.Method.GET,
                CWA_URL,
                availableDatesStr -> {
                    String[] dateStringArray = parseCwsListResponse(availableDatesStr);
                    List<DateURL> dailyUrlList = new ArrayList<>();
                    for (String str : dateStringArray) {
                        Date date = null;
                        try {
                            date = dateFormatter.parse(str);
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        if (date != null) {
                            if (date.compareTo(minDate) >= 0) {  // date >= minDate
                                dailyUrlList.add(new DateURL(date, getDailyDKsURLForDate(date)));
                            }
                        }
                    }
                    dailyUrlSubject.onNext(dailyUrlList);
                    dailyUrlSubject.onComplete();
                },
                dailyUrlSubject::onError
        );
        queue.add(stringRequest);
        return dailyUrlSubject.first(new ArrayList<>());
    }

    private static Single<List<URL>> getDailyAndHourlyUrls(RequestQueue queue, List<DateURL> dailyDateUrls) {
        if (dailyDateUrls.isEmpty()) {
            return Single.just(new ArrayList<>());
        }
        List<URL> dailyAndHourlyUrls = dailyDateUrls.stream().map(DateURL::getUrl).collect(Collectors.toList());
        Calendar c = Calendar.getInstance();
        c.setTime(dailyDateUrls.get(dailyDateUrls.size() - 1).getDate());
        c.add(Calendar.DATE, 1);
        Date currentDate = c.getTime();
        Subject<List<URL>> dailyAndHourlyUrlSubject = AsyncSubject.create();
        StringRequest stringRequest = new StringRequest(
                Request.Method.GET,
                CWA_URL+"/"+getStringFromDate(currentDate)+"/"+"hour",
                availableHoursStr -> {
                    String[] hourStringArray = parseCwsListResponse(availableHoursStr);
                    List<URL> hourlyUrls = new ArrayList<>();
                    for (String hour : hourStringArray) {
                        try {
                            URL hourlyUrl = new URL(CWA_URL+"/"+getStringFromDate(currentDate)+"/hour/"+hour);
                            hourlyUrls.add(hourlyUrl);
                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                    }
                    dailyAndHourlyUrls.addAll(hourlyUrls);
                    dailyAndHourlyUrlSubject.onNext(dailyAndHourlyUrls);
                    dailyAndHourlyUrlSubject.onComplete();
                },
                dailyAndHourlyUrlSubject::onError
        );
        queue.add(stringRequest);
        return dailyAndHourlyUrlSubject.first(new ArrayList<>());
    }

    @Override
    public Single<List<URL>> getUrls(RequestQueue queue, Date minDate) {
        return getDailyUrls(queue, minDate)
                .flatMap(dailyDateUrls -> getDailyAndHourlyUrls(queue, dailyDateUrls));
    }
}
