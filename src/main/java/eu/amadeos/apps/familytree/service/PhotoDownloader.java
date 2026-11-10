package eu.amadeos.apps.familytree.service;

import eu.amadeos.apps.familytree.domain.MediaRecord;
import eu.amadeos.apps.familytree.gedcom.GedcomConverter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Downloads photos that a family tree only links to (exports from web services often contain just the
 * address) into the tree's media folder, so they stay when the links expire. The old address is kept below
 * {@code FILE} as {@code _URL}.
 */
public final class PhotoDownloader {
    /** A single file larger than this is not downloaded. */
    static final long MAX_BYTES = 200L * 1024 * 1024;

    public interface Progress {
        void step(int done, int total, String name);
    }

    /** One downloaded file, to be put into the record on the event thread. */
    public record Downloaded(MediaRecord media, String file, String mime, String url) {
    }

    public record Result(List<Downloaded> files, int failed) {
    }

    private final HttpClient client;

    public PhotoDownloader(HttpClient client) {
        this.client = client;
    }

    public static PhotoDownloader system() {
        return new PhotoDownloader(eu.amadeos.commons.net.Network.httpClient()
                .followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(20)).build());
    }

    /**
     * Downloads the web media of the list into {@code t}'s media folder. Records are not changed here; the
     * caller applies the result with {@link #apply}.
     */
    public Result download(StoredTree t, List<MediaRecord> media, Progress progress, BooleanSupplier cancelled)
            throws IOException {
        Files.createDirectories(t.media());
        List<MediaRecord> web = media.stream().filter(MediaRecord::isWeb).toList();
        java.util.ArrayList<Downloaded> done = new java.util.ArrayList<>();
        int failed = 0;
        for (int i = 0; i < web.size(); i++) {
            if (cancelled.getAsBoolean()) break;
            MediaRecord m = web.get(i);
            String url = m.file();
            progress.step(i, web.size(), m.label());
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url.strip())).timeout(Duration.ofMinutes(2))
                        .header("User-Agent", "amade-familytree").GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() / 100 != 2) {
                    response.body().close();
                    failed++;
                    continue;
                }
                String mime = response.headers().firstValue("Content-Type").orElse("")
                        .split(";")[0].strip().toLowerCase(Locale.ROOT);
                Path target = TreeStore.freeFile(t.media(), fileName(url, mime, m.id()));
                Path part = target.resolveSibling(target.getFileName() + ".part");
                long n = 0;
                try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(part)) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        n += r;
                        if (n > MAX_BYTES) throw new IOException("too large");
                        out.write(buf, 0, r);
                    }
                } catch (IOException e) {
                    Files.deleteIfExists(part);
                    throw e;
                }
                Files.move(part, target);
                String form = mime.isEmpty() || mime.equals("application/octet-stream")
                        ? GedcomConverter.mime(extension(target.getFileName().toString())) : mime;
                done.add(new Downloaded(m, StoredTree.MEDIA + "/" + target.getFileName(), form, url));
            } catch (IOException | IllegalArgumentException e) {
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        progress.step(web.size(), web.size(), "");
        return new Result(done, failed);
    }

    /** Points the records to the downloaded files. */
    public static void apply(Result result) {
        for (Downloaded d : result.files()) {
            d.media().setFile(d.file());
            if (d.mime() != null && !d.mime().isEmpty()) d.media().setForm(d.mime());
            d.media().fileNode().ifPresent(n -> n.set("_URL", d.url()));
        }
    }

    /** A file name from the address ("…/12345_678.jpg?x=1" → "12345_678.jpg"), with an extension. */
    static String fileName(String url, String mime, String id) {
        String path = url;
        int q = path.indexOf('?');
        if (q >= 0) path = path.substring(0, q);
        int hash = path.indexOf('#');
        if (hash >= 0) path = path.substring(0, hash);
        String name = path.substring(path.lastIndexOf('/') + 1);
        try {
            name = java.net.URLDecoder.decode(name, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            // Keep the name as written.
        }
        if (name.isBlank()) name = id == null ? "photo" : id;
        if (extension(name).isEmpty()) {
            String form = GedcomConverter.form551(mime);
            if (form != null && !form.isEmpty() && !form.contains("/") && !form.equals("octet-stream")) {
                name += "." + form;
            } else {
                name += ".jpg";
            }
        }
        return name;
    }

    static String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 && dot < name.length() - 1 && name.length() - dot <= 6 ? name.substring(dot + 1) : "";
    }
}
