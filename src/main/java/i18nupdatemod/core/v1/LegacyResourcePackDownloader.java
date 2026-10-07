package i18nupdatemod.core.v1;

import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.entity.GameMetaData;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class LegacyResourcePackDownloader {
    private LegacyResourcePackDownloader() {
    }

    public static List<Path> download(GameMetaData metadata, String loader,
                                      Path resourcePackDirectory, Path cacheRoot) throws Exception {
        Files.createDirectories(resourcePackDirectory);

        List<AssetDownloadDetail> downloads = LegacyConfig.getLegacyDownloads(metadata, loader);
        if (downloads.isEmpty()) {
            return new ArrayList<>();
        }

        ResourcePackHttp source = ResourcePackHttp.configured();
        int workerCount = Math.max(1, Math.min(source.parallelism(), downloads.size()));
        List<DownloadRequest> requests = new ArrayList<>(downloads.size());
        Map<Path, ReentrantLock> cacheLocks = new HashMap<>();
        for (AssetDownloadDetail item : downloads) {
            Path cachePath = cacheRoot.resolve(item.targetVersion).resolve(item.fileName);
            Files.createDirectories(cachePath.getParent());

            Path lockKey = cachePath.toAbsolutePath().normalize();
            ReentrantLock cacheLock = cacheLocks.get(lockKey);
            if (cacheLock == null) {
                cacheLock = new ReentrantLock();
                cacheLocks.put(lockKey, cacheLock);
            }
            requests.add(new DownloadRequest(
                    source, item, resourcePackDirectory, cachePath, cacheLock));
        }

        ExecutorService executor = Executors.newFixedThreadPool(workerCount, downloadThreadFactory());
        List<Future<Path>> futures = new ArrayList<>(requests.size());
        try {
            for (DownloadRequest request : requests) {
                futures.add(executor.submit(() -> downloadOne(request)));
            }
            executor.shutdown();

            // Future order is the metadata's convertFrom order, which is also
            // the resource-pack merge precedence order.
            List<Path> sourcePaths = new ArrayList<>(futures.size());
            for (Future<Path> future : futures) {
                sourcePaths.add(future.get());
            }
            awaitTermination(executor);
            return sourcePaths;
        } catch (InterruptedException e) {
            cancelAndJoin(executor, futures);
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Interrupted while downloading legacy resource packs");
            interrupted.initCause(e);
            throw interrupted;
        } catch (ExecutionException e) {
            cancelAndJoin(executor, futures);
            return rethrowTaskFailure(e.getCause());
        } catch (RuntimeException e) {
            cancelAndJoin(executor, futures);
            throw e;
        } catch (Error e) {
            cancelAndJoin(executor, futures);
            throw e;
        }
    }

    private static Path downloadOne(DownloadRequest request) throws Exception {
        try {
            request.cacheLock.lockInterruptibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Interrupted while waiting for legacy resource pack cache");
            interrupted.initCause(e);
            throw interrupted;
        }
        try {
            ResourcePack resourcePack = new ResourcePack(
                    request.resourcePackDirectory.resolve(request.detail.fileName),
                    request.cachePath);
            resourcePack.checkUpdate(
                    request.source, request.detail.fileName, request.detail.md5FileName);
            return resourcePack.getTmpFilePath();
        } finally {
            request.cacheLock.unlock();
        }
    }

    private static ThreadFactory downloadThreadFactory() {
        return new ThreadFactory() {
            private int nextId;

            @Override
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "i18nupdatemod-v1-download-" + (++nextId));
                thread.setDaemon(false);
                return thread;
            }
        };
    }

    private static void awaitTermination(ExecutorService executor) throws InterruptedException {
        while (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            // A fixed executor with a finite submission set eventually terminates.
        }
    }

    private static void cancelAndJoin(ExecutorService executor,
                                      List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executor.shutdownNow();
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<Path> rethrowTaskFailure(Throwable failure)
            throws IOException, NoSuchAlgorithmException, InterruptedException {
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof NoSuchAlgorithmException) {
            throw (NoSuchAlgorithmException) failure;
        }
        if (failure instanceof InterruptedException) {
            throw (InterruptedException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IOException("Legacy resource pack download failed", failure);
    }

    private static final class DownloadRequest {
        final ResourcePackHttp source;
        final AssetDownloadDetail detail;
        final Path resourcePackDirectory;
        final Path cachePath;
        final ReentrantLock cacheLock;

        DownloadRequest(ResourcePackHttp source, AssetDownloadDetail detail,
                        Path resourcePackDirectory, Path cachePath,
                        ReentrantLock cacheLock) {
            this.source = source;
            this.detail = detail;
            this.resourcePackDirectory = resourcePackDirectory;
            this.cachePath = cachePath;
            this.cacheLock = cacheLock;
        }
    }
}
