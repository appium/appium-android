/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.appium.uiautomator2.unittest.test.internal;

import android.os.SystemClock;

import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import io.appium.uiautomator2.common.exceptions.UiAutomator2Exception;
import io.appium.uiautomator2.unittest.test.Config;

import static android.os.SystemClock.elapsedRealtime;

public abstract class Client {

    private static final String JSON = "application/json; charset=utf-8";
    private static final int CONNECT_TIMEOUT_MS = 15 * 1000;
    private static final int READ_TIMEOUT_MS = 2 * 60 * 1000;

    public static Response get(final String path) {
        return get(WebDriverSession.getInstance().getBaseUrl(), path);
    }

    private static Response get(final String baseUrl, final String path) {
        return execute("GET", baseUrl + path, null);
    }

    public static Response post(final String path, final JsonObject body) {
        return post(WebDriverSession.getInstance().getBaseUrl(), path, body);
    }

    public static Response post(final String baseUrl, final String path, final JsonObject body) {
        return execute("POST", baseUrl + path, body.toString());
    }

    public static Response delete() {
        return execute("DELETE", WebDriverSession.getInstance().getBaseUrl(),
                new JsonObject().toString());
    }

    public static void waitForNettyStatus(final NettyStatus status) {
        Logger.debug("Waiting until netty server will be " + status);
        long start = elapsedRealtime();
        NettyStatus actualStatus;
        do {
            try {
                get(Config.HOST, "/status");
                actualStatus = NettyStatus.ONLINE;
            } catch (Exception e) {
                actualStatus = NettyStatus.OFFLINE;
            }
            Logger.info("Netty server status is " + actualStatus);
            if (actualStatus.equals(status)) {
                return;
            }
            SystemClock.sleep(500);
        } while (elapsedRealtime() - start < Config.NETTY_STATUS_TIMEOUT);
        throw new TimeoutException(String.format("netty status. Expected:%s; Actual:%s;",
                status, actualStatus));
    }

    private static Response execute(final String method, final String url, final String body) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
            connection.setReadTimeout(READ_TIMEOUT_MS);
            connection.setRequestMethod(method);
            if (body != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", JSON);
                connection.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
            }
            final int code = connection.getResponseCode();
            final InputStream stream = code >= HttpURLConnection.HTTP_BAD_REQUEST
                    ? connection.getErrorStream() : connection.getInputStream();
            return new Response(code, readFully(stream));
        } catch (IOException e) {
            throw new UiAutomator2Exception(method + " \"" + url + "\" failed. ", e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readFully(final InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        try (InputStream in = stream) {
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8.name());
        }
    }

}
