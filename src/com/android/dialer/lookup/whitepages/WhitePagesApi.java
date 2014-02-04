/*
 * Copyright (C) 2014 Xiao-Long Chen <chenxiaolong@cxl.epac.to>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.dialer.lookup.whitepages;

import com.android.services.telephony.common.MoreStrings;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

public class WhitePagesApi {
    private static final String TAG = WhitePagesApi.class.getSimpleName();

    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64; rv:26.0) Gecko/20100101 Firefox/26.0";
    private static final String LOOKUP_URL =
            "http://www.whitepages.com/search/ReversePhone?full_phone=";
    private static final String[] COOKIE_REGEXES = {
            "distil_RID=([A-Za-z0-9\\-]+)", "PID=([A-Za-z0-9\\-]+)" };
    private static final String COOKIE = "D_UID";

    private String mNumber = null;
    private String mOutput = null;
    private String mCookie = null;
    private ContactInfo mInfo = null;

    public WhitePagesApi(String number) {
        mNumber = number;
    }

    private void fetchPage() throws IOException {
        mOutput = httpGet(LOOKUP_URL + mNumber);
    }

    private void extractCookie() throws IOException {
        for (String regex : COOKIE_REGEXES) {
            Pattern p = Pattern.compile(regex, Pattern.DOTALL);
            Matcher m = p.matcher(mOutput);
            if (m.find()) {
                mCookie = m.group(1).trim();
                break;
            }
        }

        if (mCookie == null) {
            throw new IOException("HTML response does not contain cookie value");
        }
    }

    private String httpGet(String url) throws IOException {
        String[] split = url.split("=");
        Log.d(TAG, "Fetching " + split[0] + "="
                + MoreStrings.toSafeString(split[1]));

        HttpClient client = new DefaultHttpClient();
        HttpGet get = new HttpGet(url);

        get.setHeader("User-Agent", USER_AGENT);

        if (mCookie != null) {
            get.setHeader("Cookie", COOKIE + "=" + mCookie);
        }

        HttpResponse response = client.execute(get);
        int status = response.getStatusLine().getStatusCode();

        // Android's org.apache.http doesn't have the RedirectStrategy class
        if (status == HttpStatus.SC_MOVED_PERMANENTLY
                || status == HttpStatus.SC_MOVED_TEMPORARILY) {
            Header[] headers = response.getHeaders("Location");

            if (headers != null && headers.length != 0) {
                String newUrl = headers[headers.length - 1].getValue();
                return httpGet(newUrl);
            } else {
                return null;
            }
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        response.getEntity().writeTo(out);

        return new String(out.toByteArray());
    }

    private void buildContactInfo() {
        Matcher m;

        // Name
        Pattern regexName = Pattern
                .compile("<h2.*?>Send (.*?)&#39;s details to phone</h2>",
                        Pattern.DOTALL);
        String name = null;

        m = regexName.matcher(mOutput);
        if (m.find()) {
            name = m.group(1).trim();
        }

        // Use summary if name doesn't exist
        if (name == null) {
            Pattern regexSummary = Pattern.compile(
                    "<span\\s*class=\"subtitle.*?>\\s*\n?(.*?)\n?\\s*</span>",
                    Pattern.DOTALL);

            m = regexSummary.matcher(mOutput);
            if (m.find()) {
                name = m.group(1).trim();
            }
        }

        if (name != null) {
            name = name.replaceAll("&amp;", "&");
        }

        // Formatted phone number
        Pattern regexPhoneNumber = Pattern.compile(
                "Full Number:</span>([0-9\\-\\+\\(\\)]+)</li>", Pattern.DOTALL);
        String phoneNumber = null;

        m = regexPhoneNumber.matcher(mOutput);
        if (m.find()) {
            phoneNumber = m.group(1).trim();
        }

        // Address
        String regexBase = "<span\\s+class=\"%s[^\"]+\"\\s*>([^<]*)</span>";

        Pattern regexAddressPrimary = Pattern.compile(
                String.format(regexBase, "address-primary"), Pattern.DOTALL);
        Pattern regexAddressSecondary = Pattern.compile(
                String.format(regexBase, "address-secondary"), Pattern.DOTALL);
        Pattern regexAddressLocation = Pattern.compile(
                String.format(regexBase, "address-location"), Pattern.DOTALL);

        String addressPrimary = null;
        String addressSecondary = null;
        String addressLocation = null;

        m = regexAddressPrimary.matcher(mOutput);
        if (m.find()) {
            addressPrimary = m.group(1).trim();
        }

        m = regexAddressSecondary.matcher(mOutput);
        if (m.find()) {
            addressSecondary = m.group(1).trim();
        }

        m = regexAddressLocation.matcher(mOutput);
        if (m.find()) {
            addressLocation = m.group(1).trim();
        }

        StringBuilder sb = new StringBuilder();

        if (addressPrimary != null && addressPrimary.length() != 0) {
            sb.append(addressPrimary);
        }
        if (addressSecondary != null && addressSecondary.length() != 0) {
            sb.append(", ");
            sb.append(addressSecondary);
        }
        if (addressLocation != null && addressLocation.length() != 0) {
            sb.append(", ");
            sb.append(addressLocation);
        }

        String address = sb.toString();
        if (address.length() == 0) {
            address = null;
        }

        ContactInfo info = new ContactInfo();
        info.name = name;
        info.address = address;
        info.formattedNumber = phoneNumber != null ? phoneNumber : mNumber;
        info.website = LOOKUP_URL + info.formattedNumber;
        mInfo = info;
    }

    public ContactInfo getContactInfo() throws IOException {
        if (mInfo == null) {
            // We'll fetch the page twice every time since we do not what
            // restrictions on the cookies are present (IP checks, time limits,
            // etc.)
            fetchPage();
            extractCookie();
            fetchPage();

            buildContactInfo();
        }

        return mInfo;
    }

    public static class ContactInfo {
        String name;
        String address;
        String formattedNumber;
        String website;
    }
}
