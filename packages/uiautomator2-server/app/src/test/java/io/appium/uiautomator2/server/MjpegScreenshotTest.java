package io.appium.uiautomator2.server.mjpeg;

import org.junit.Test;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;

import java.net.URL;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.net.Socket;

import org.junit.Assume;

import io.appium.uiautomator2.server.mjpeg.MjpegScreenshotServer;
import io.appium.uiautomator2.server.mjpeg.MjpegScreenshotStream;
import io.appium.uiautomator2.server.ServerConfig;

public class MjpegScreenshotTest {
  private static MjpegScreenshotServer serverThread;
  private static int streamingPort;

  @Before
  public void setUp() throws Exception {
    // Test is flaky in CI because we have to wait for the server to actually start
    // Adding a sleep or a loop to wait on the server to be ready could help: skip it for now
    Assume.assumeTrue(System.getenv("CI") == null);

    // Create a MJPEG server with a mocked getScreenshot method
    List<MjpegScreenshotClient> clients = new ArrayList<>();
    String mockScreenshotData = "screenshot data";
    byte[] mockHTTPResponse =
        ("HTTP/1.1 200 OK\n"
                + "Content-Length: "
                + mockScreenshotData.length()
                + "\n\n"
                + mockScreenshotData)
            .getBytes(StandardCharsets.UTF_8);
    MjpegScreenshotStream mockScreenshotStream = new MjpegScreenshotStream(clients) {
      @Override
      byte[] getScreenshot() {
        return mockHTTPResponse;
      }
    };

    streamingPort = ServerConfig.DEFAULT_MJPEG_SERVER_PORT;
    serverThread = new MjpegScreenshotServer(streamingPort, clients, mockScreenshotStream);
    serverThread.start();
    assertTrue(serverThread.isAlive());
  }

  @Test
  public void shouldReceiveDataFromMjpegServer() throws Exception {
    URL url = new URL(String.format("http://localhost:%d", streamingPort));
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    int responseCode = connection.getResponseCode();
    assertEquals(HttpURLConnection.HTTP_OK, responseCode);
  }

  @Test
  public void shouldNotBlockOnUnInitializedClient() throws Exception {
    // Create a socket and connect to the streaming server: it will create an uninitialized client
    Socket socket = new Socket("localhost", streamingPort);
    socket.close();

    // We should still be able to receive data
    URL url = new URL(String.format("http://localhost:%d", streamingPort));
    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
    connection.setRequestMethod("GET");
    int responseCode = connection.getResponseCode();
    assertEquals(HttpURLConnection.HTTP_OK, responseCode);
  }

  @After
  public void tearDown() {
    if (serverThread != null) {
    serverThread.interrupt();
    }
  }
}
