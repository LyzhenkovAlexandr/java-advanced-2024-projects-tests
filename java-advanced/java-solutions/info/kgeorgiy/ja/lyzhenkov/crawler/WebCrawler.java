package info.kgeorgiy.ja.lyzhenkov.crawler;

import info.kgeorgiy.java.advanced.crawler.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

/**
 * This class recursively crawls sites to a given depth and with a limit on the number
 * of possible downloads at a time from one host.
 */
public class WebCrawler implements NewCrawler {

    private final Downloader downloader;
    private final ExecutorService downloaderService;
    private final ExecutorService extractorService;
    private final ConcurrentMap<String, QueueDownloadHost> hosts;
    private final int perHost;

    public WebCrawler(Downloader downloader, int downloaders, int extractors, int perHost) {
        Objects.requireNonNull(downloader, "Downloader is null");
        if (downloaders <= 0 || extractors <= 0 || perHost <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Downloaders, extractors and perHost must be positive, but found: %d, %d and %d",
                    downloaders, extractors, perHost));
        }
        this.downloader = downloader;
        this.downloaderService = Executors.newFixedThreadPool(downloaders);
        this.extractorService = Executors.newFixedThreadPool(extractors);
        this.perHost = perHost;
        this.hosts = new ConcurrentHashMap<>();
    }

    @Override
    public Result download(String url, int depth, Set<String> excludes) {
        var data = new Data();
        data.needDownload.add(url);
        data.cacheURL.add(url);
        var waiter = new Phaser();
        waiter.register();

        IntStream.range(0, depth).forEach(curDepth -> {
            var needDownloadCopy = new HashSet<>(data.needDownload);
            data.needDownload.clear();
            var isLastLayer = curDepth == depth - 1;

            needDownloadCopy.forEach(curURL -> {
                var isContains = excludes.stream().anyMatch(curURL::contains);
                if (!isContains) {
                    try {
                        var host = URLUtils.getHost(curURL);
                        waiter.register();
                        var queueDownloadHost = hosts.computeIfAbsent(host, ignored -> new QueueDownloadHost());
                        queueDownloadHost.addTask(downloadDocument(curURL, data, waiter, queueDownloadHost, isLastLayer));
                    } catch (final MalformedURLException e) {
                        data.errors.put(url, e);
                    }
                }
            });
            waiter.arriveAndAwaitAdvance();
        });
        return new Result(data.downloadedURL.parallelStream().toList(), data.errors);
    }

    private Runnable downloadDocument(
            final String url,
            final Data data,
            final Phaser waiter,
            final QueueDownloadHost queueDownloadHost,
            final boolean isLastLayer
    ) {
        return () -> {
            try {
                var document = downloader.download(url);
                data.downloadedURL.add(url);
                if (!isLastLayer) {
                    waiter.register();
                    extractorService.submit(extractLinks(document, data, waiter, url));
                }
            } catch (final IOException e) {
                data.errors.put(url, e);
            } finally {
                waiter.arriveAndDeregister();
                queueDownloadHost.finishedTask();
            }
        };
    }

    private Runnable extractLinks(final Document document, final Data data, final Phaser waiter, final String url) {
        return () -> {
            try {
                document.extractLinks().parallelStream().filter(data.cacheURL::add).forEach(data.needDownload::add);
            } catch (final IOException e) {
                System.err.printf("Error extraction links from document by URL: %s%n%s%n", url, e.getMessage());
            } finally {
                waiter.arriveAndDeregister();
            }
        };
    }

    @Override
    public void close() {
        downloaderService.shutdownNow();
        extractorService.shutdownNow();
        var errorMessage = "WebCrawler couldn't close correctly";
        try {
            if (!downloaderService.awaitTermination(10L, TimeUnit.SECONDS) ||
                    !extractorService.awaitTermination(10L, TimeUnit.SECONDS)) {
                System.err.println(errorMessage);
            }
        } catch (final InterruptedException e) {
            System.err.println(errorMessage);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <ul>
     *     <li>url - page address</li>
     *     <li>depth - depth of site crawl</li>
     *     <li>downloads — maximum number of simultaneously downloaded pages</li>
     *     <li>extractors - the maximum number of pages from which links are simultaneously extracted</li>
     *     <li>perHost - the maximum number of pages downloaded simultaneously from one host</li>
     * </ul>
     *
     * @param args launch arguments
     */
    public static void main(final String[] args) {
        var invalidInput = "Input must be: url [depth [downloads [extractors [perHost]]]]";
        if (args == null || args.length < 1 || args.length > 5 || Arrays.stream(args).anyMatch(Objects::isNull)) {
            System.err.println(invalidInput);
            return;
        }
        var url = args[0];
        var parsedArgs = new int[4];
        Arrays.fill(parsedArgs, 6);
        final Downloader mainDownloader;
        try {
            for (int i = 1; i < args.length; i++) {
                parsedArgs[i - 1] = Integer.parseInt(args[i]);
            }
            if (args.length == 1) {
                parsedArgs[0] = 1;
            }
            mainDownloader = new CachingDownloader(0.1);
        } catch (final NumberFormatException e) {
            System.err.println(invalidInput);
            return;
        } catch (final IOException e) {
            System.err.println(e.getMessage());
            return;
        }
        try (var crawler = new WebCrawler(mainDownloader, parsedArgs[1], parsedArgs[2], parsedArgs[3])) {
            var result = crawler.download(url, parsedArgs[0]);
            System.out.println("Downloaded URL:");
            result.getDownloaded().forEach(System.out::println);
            System.out.println();
            System.out.println("An error occurred during loading at the URL:");
            result.getErrors().forEach((curURL, error) -> System.out.printf("%s%n%s%n%n", curURL, error.getMessage()));
        }
    }

    private final class QueueDownloadHost {
        private final Queue<Runnable> queue = new ArrayDeque<>(500);
        private int busy;

        private void addTask(final Runnable task) {
            if (busy >= perHost) {
                queue.add(task);
            } else {
                busy++;
                downloaderService.submit(task);
            }
        }

        private synchronized void finishedTask() {
            final Runnable task = queue.poll();
            if (task != null) {
                downloaderService.submit(task);
            } else {
                busy--;
            }
        }
    }

    private static class Data {
        private final ConcurrentMap<String, IOException> errors;
        private final Set<String> needDownload;
        private final Set<String> cacheURL;
        private final Set<String> downloadedURL;

        Data() {
            this.errors = new ConcurrentHashMap<>();
            this.needDownload = ConcurrentHashMap.newKeySet();
            this.cacheURL = ConcurrentHashMap.newKeySet();
            this.downloadedURL = ConcurrentHashMap.newKeySet();
        }
    }
}
