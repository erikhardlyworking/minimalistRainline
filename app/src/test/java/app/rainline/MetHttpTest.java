package app.rainline;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.TreeMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;
import java.util.zip.DeflaterOutputStream;
import static org.junit.Assert.*;

public class MetHttpTest {
    private static final String ENDPOINT = "https://api.met.no/weatherapi/nowcast/2.0/complete?lat=60.0000&lon=10.0000";

    @Test public void redirectsKeepIdentificationAndConditionalHeaders() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FakeConnection first = new FakeConnection(new URL(ENDPOINT), 302);
        first.headers.put("Location", "/weatherapi/nowcast/2.0/complete?lat=60.0000&lon=10.0000");
        FakeConnection second = new FakeConnection(new URL(ENDPOINT), 304);
        HttpURLConnection result = MetHttp.open(new URL(ENDPOINT), "unchanged-server-header", "\"forecast-1\"", url ->
                calls.getAndIncrement() == 0 ? first : second);
        assertSame(second, result);
        assertTrue(first.disconnected);
        assertFalse(second.disconnected);
        assertEquals(2, calls.get());
        for (FakeConnection connection : new FakeConnection[]{first, second}) {
            assertTrue(connection.getRequestProperty("User-Agent").contains("https://github.com/erikhardlyworking/minimalistRainline"));
            assertEquals("gzip, deflate", connection.getRequestProperty("Accept-Encoding"));
            assertEquals("unchanged-server-header", connection.getRequestProperty("If-Modified-Since"));
            assertEquals("\"forecast-1\"", connection.getRequestProperty("If-None-Match"));
        }
    }
    @Test public void redirectsNeverLeakCoordinatesOutsideMetHttps() throws Exception {
        for (String location : new String[]{"http://api.met.no/forecast", "https://example.com/forecast",
                "https://api.met.no:444/forecast", "https://someone@api.met.no/forecast"}) {
            AtomicInteger calls = new AtomicInteger();
            try {
                MetHttp.open(new URL(ENDPOINT), "", "", url -> {
                    calls.incrementAndGet();
                    FakeConnection connection = new FakeConnection(url, 307);
                    connection.headers.put("Location", location);
                    return connection;
                });
                fail("Unsupported redirect accepted");
            } catch (UpdateIssue.Failure expected) { assertEquals(UpdateIssue.REDIRECT_ERROR, expected.issue); }
            assertEquals(1, calls.get());
        }
    }
    @Test public void redirectLoopsAreBounded() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        try {
            MetHttp.open(new URL(ENDPOINT), "", "", url -> {
                calls.incrementAndGet();
                FakeConnection connection = new FakeConnection(url, 308);
                connection.headers.put("Location", ENDPOINT);
                return connection;
            });
            fail("Redirect loop accepted");
        } catch (UpdateIssue.Failure expected) { assertEquals(UpdateIssue.REDIRECT_ERROR, expected.issue); }
        assertEquals(5, calls.get());
    }
    @Test public void decodesGzipAndDeflateWithoutChangingJson() throws Exception {
        String json = "{\"forecast\":\"rain – sleet\"}";
        for (String encoding : new String[]{"gzip", "deflate", "identity"}) {
            FakeConnection connection = new FakeConnection(new URL(ENDPOINT), 200);
            connection.headers.put("Content-Encoding", encoding);
            connection.body = compressed(json.getBytes(StandardCharsets.UTF_8), encoding);
            assertEquals(json, MetHttp.body(connection));
        }
    }
    @Test public void responseLimitAppliesAfterDecompression() throws Exception {
        FakeConnection connection = new FakeConnection(new URL(ENDPOINT), 200);
        connection.headers.put("Content-Encoding", "gzip");
        connection.body = compressed(new byte[1_000_001], "gzip");
        try { MetHttp.body(connection); fail("Oversized decoded response accepted"); }
        catch (UpdateIssue.Failure expected) { assertEquals(UpdateIssue.INVALID_RESPONSE, expected.issue); }
    }
    private static byte[] compressed(byte[] bytes, String encoding) throws IOException {
        if ("identity".equals(encoding)) return bytes;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (java.io.OutputStream compressor = "gzip".equals(encoding) ? new GZIPOutputStream(output) : new DeflaterOutputStream(output)) {
            compressor.write(bytes);
        }
        return output.toByteArray();
    }
    private static final class FakeConnection extends HttpURLConnection {
        final Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        byte[] body = new byte[0];
        boolean disconnected;
        FakeConnection(URL url, int code) { super(url); responseCode = code; }
        @Override public int getResponseCode() { return responseCode; }
        @Override public String getHeaderField(String name) { return headers.get(name); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(body); }
        @Override public void connect() { }
        @Override public void disconnect() { disconnected = true; }
        @Override public boolean usingProxy() { return false; }
    }
}
