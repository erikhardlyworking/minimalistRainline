package app.rainline;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/** Bounded HTTPS redirects and decompression for MET's HTTP transport. */
final class MetHttp {
    interface ConnectionFactory { HttpURLConnection open(URL url) throws IOException; }

    static String userAgent() {
        String contact = BuildConfig.MET_CONTACT.isEmpty() ? BuildConfig.SOURCE_URL : BuildConfig.MET_CONTACT;
        return "Rainline/" + BuildConfig.VERSION_NAME + " (" + contact + ")";
    }

    static HttpURLConnection open(URL url, String modified, String etag, ConnectionFactory factory) throws IOException {
        for (int redirects = 0; ; redirects++) {
            HttpURLConnection connection = factory.open(url);
            try {
                connection.setConnectTimeout(12_000);
                connection.setReadTimeout(12_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("User-Agent", userAgent());
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Accept-Encoding", "gzip, deflate");
                if (!modified.isEmpty()) connection.setRequestProperty("If-Modified-Since", modified);
                if (!etag.isEmpty()) connection.setRequestProperty("If-None-Match", etag);
                int code = connection.getResponseCode();
                if (code != 301 && code != 302 && code != 303 && code != 307 && code != 308) return connection;
                String location = connection.getHeaderField("Location");
                if (redirects >= 4 || location == null || location.isEmpty())
                    throw UpdateIssue.REDIRECT_ERROR.failure("The weather service returned an unusable redirect.");
                URL next = new URL(url, location);
                // Keep coordinates within MET's HTTPS API; never forward them to an unrelated host.
                if (!"https".equalsIgnoreCase(next.getProtocol()) || !"api.met.no".equalsIgnoreCase(next.getHost())
                        || (next.getPort() != -1 && next.getPort() != 443) || next.getUserInfo() != null)
                    throw UpdateIssue.REDIRECT_ERROR.failure("The weather service redirected outside its supported HTTPS API.");
                url = next;
            } catch (IOException e) {
                connection.disconnect();
                throw e;
            }
            connection.disconnect();
        }
    }

    static String body(HttpURLConnection connection) throws IOException {
        try (InputStream raw = connection.getInputStream()) {
            String encoding = connection.getContentEncoding();
            InputStream decoded;
            if (encoding == null || encoding.isEmpty() || "identity".equalsIgnoreCase(encoding)) decoded = raw;
            else if ("gzip".equalsIgnoreCase(encoding)) decoded = new GZIPInputStream(raw);
            else if ("deflate".equalsIgnoreCase(encoding)) decoded = new InflaterInputStream(raw);
            else throw UpdateIssue.INVALID_RESPONSE.failure("The weather service used unsupported compression.");
            try (InputStream input = decoded; ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    if (Thread.currentThread().isInterrupted()) throw new IOException("Update interrupted");
                    if (bytes.size() + count > 1_000_000)
                        throw UpdateIssue.INVALID_RESPONSE.failure("Unexpected weather response size.");
                    bytes.write(buffer, 0, count);
                }
                return bytes.toString(StandardCharsets.UTF_8.name());
            }
        }
    }
}
