package app.rainline;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ForecastCache {
    public static final class Entry {
        public String body, retainedBody = "", lastModified = "", etag = "";
        public long expiresAt, checkedAt, retryAt;
        public boolean deprecated;
        public Forecast forecast() throws JSONException { return Forecast.parse(body); }
        public String displayBody(long now) throws JSONException {
            if (ForecastWindow.hasUpcomingData(forecast(), now) || retainedBody.isEmpty()) return body;
            try {
                if (ForecastWindow.hasUpcomingData(Forecast.parse(retainedBody), now)) return retainedBody;
            } catch (JSONException ignored) { /* A damaged fallback must not hide a valid API response. */ }
            return body;
        }
        public Forecast displayForecast(long now) throws JSONException { return Forecast.parse(displayBody(now)); }
    }
    private final File directory;
    public ForecastCache(Context context) {
        directory = new File(context.getCacheDir(), "nowcast");
    }
    private AtomicFile file(String key) {
        if (!key.matches("-?\\d{1,3}\\.\\d{4},-?\\d{1,3}\\.\\d{4}"))
            throw new IllegalArgumentException("Invalid cache key");
        return new AtomicFile(new File(directory, key + ".json"));
    }
    public Entry read(String key) {
        try {
            JSONObject o = new JSONObject(new String(file(key).readFully(), StandardCharsets.UTF_8));
            Entry e = new Entry();
            e.body = o.getString("body");
            e.retainedBody = o.optString("retainedBody");
            e.expiresAt = o.optLong("expiresAt");
            e.checkedAt = o.optLong("checkedAt");
            e.retryAt = o.optLong("retryAt");
            e.lastModified = o.optString("lastModified");
            e.etag = o.optString("etag");
            e.deprecated = o.optBoolean("deprecated");
            e.forecast(); // Treat a corrupt cache as unavailable, not as dry.
            return e;
        } catch (IOException | JSONException e) { return null; }
    }
    public void write(String key, Entry e) throws IOException {
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Could not create weather cache");
        AtomicFile target = file(key);
        FileOutputStream stream = null;
        try {
            JSONObject o = new JSONObject();
            o.put("body", e.body).put("expiresAt", e.expiresAt).put("checkedAt", e.checkedAt);
            o.put("retainedBody", e.retainedBody);
            o.put("retryAt", e.retryAt).put("lastModified", e.lastModified).put("etag", e.etag);
            o.put("deprecated", e.deprecated);
            stream = target.startWrite();
            stream.write(o.toString().getBytes(StandardCharsets.UTF_8));
            target.finishWrite(stream);
        } catch (IOException | JSONException ex) {
            if (stream != null) target.failWrite(stream);
            throw new IOException("Could not save weather cache", ex);
        }
        // Bound retained coordinate history. Settings only retain the selected location.
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".json"));
        if (files != null && files.length > 16) {
            java.util.Arrays.sort(files, java.util.Comparator.comparingLong(File::lastModified));
            for (int i = 0; i < files.length - 16; i++) new AtomicFile(files[i]).delete();
        }
    }
}
