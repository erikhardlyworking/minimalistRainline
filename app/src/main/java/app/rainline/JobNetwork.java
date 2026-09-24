package app.rainline;

import android.net.Network;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/** Volatile so redirects/subsequent widgets use an updated JobScheduler assignment. */
final class JobNetwork implements MetHttp.ConnectionFactory {
    private final boolean assignedNetworkRequired;
    private volatile Network network;
    JobNetwork(boolean assignedNetworkRequired, Network network) {
        this.assignedNetworkRequired = assignedNetworkRequired;
        this.network = network;
    }
    void changed(Network replacement) { network = replacement; }
    @Override public HttpURLConnection open(URL url) throws IOException {
        if (Thread.currentThread().isInterrupted()) throw new IOException("Refresh cancelled");
        Network current = network;
        if (assignedNetworkRequired && current == null) throw new IOException("Job network unavailable");
        return (HttpURLConnection) (current == null ? url.openConnection() : current.openConnection(url));
    }
}
