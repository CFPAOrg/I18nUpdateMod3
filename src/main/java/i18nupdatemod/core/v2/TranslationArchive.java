package i18nupdatemod.core.v2;

import org.tukaani.xz.LZMAInputStream;

import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Converts the tar.lzma translation archives served by the translation index
 * into resource-pack ZIP files.  The input is decoded as a lzip stream when it
 * has the lzip signature; legacy .lzma-alone files are accepted as well.
 */
public final class TranslationArchive {

    /**
     * Signals a local archive read/write failure rather than malformed
     * translation content.  The downloader propagates this to the
     * whole-pipeline legacy fallback.
     */
    public static final class LocalIoException extends IOException {
        public LocalIoException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private static final int LZIP_HEADER_SIZE = 6;
    private static final int LZIP_TRAILER_SIZE = 20;
    private static final int TAR_BLOCK_SIZE = 512;
    private static final int COPY_BUFFER_SIZE = 32 * 1024;
    private static final int MAX_METADATA_SIZE = 1024 * 1024;
    private static final int MAX_ZIP_NAME_BYTES = 65535;
    private static final int MAX_XZ_MEMORY_KIB = 64 * 1024;
    private static final long MAX_LZIP_DICTIONARY_SIZE = 64L * 1024L * 1024L;
    private static final byte[] LZIP_MAGIC = new byte[]{'L', 'Z', 'I', 'P'};

    private TranslationArchive() {
    }

    /**
     * Decompresses and converts one translation archive.
     *
     * @param archive      compressed lzip or legacy lzma archive
     * @param outputZip    destination ZIP path
     * @param rawNamespace mod namespace to place below {@code assets/}
     * @throws IOException when the archive is malformed or cannot be written
     */
    public static void unpack(Path archive, Path outputZip, String rawNamespace) throws IOException {
        if (archive == null || outputZip == null) {
            throw new NullPointerException("archive and outputZip must not be null");
        }
        String namespace = normalizeNamespace(rawNamespace);
        Path parent = outputZip.getParent();
        if (parent != null) {
            createDirectoriesLocal(parent);
        }

        Path temporary = outputZip.resolveSibling(outputZip.getFileName().toString() + ".tmp");
        deleteLocal(temporary);
        try {
            try (LocalInputStream fileInput = openInput(archive);
                 CountingInputStream counted = new CountingInputStream(fileInput);
                 PushbackInputStream probeInput = new PushbackInputStream(counted, LZIP_HEADER_SIZE);
                 LocalOutputStream temporaryOutput = openOutput(temporary);
                 ZipOutputStream zip = new ZipOutputStream(temporaryOutput, StandardCharsets.UTF_8)) {
                byte[] probeBytes = new byte[LZIP_HEADER_SIZE];
                int probeLength = readAtMost(probeInput, probeBytes, 0, probeBytes.length);
                boolean looksLikeLzip = probeLength >= LZIP_MAGIC.length
                        && hasLzipMagic(probeBytes);

                InputStream decompressed;
                if (looksLikeLzip) {
                    if (probeLength != LZIP_HEADER_SIZE) {
                        throw new IOException("Truncated lzip header");
                    }
                    decompressed = new LzipInputStream(probeInput, counted, probeBytes);
                } else {
                    if (probeLength > 0) {
                        probeInput.unread(probeBytes, 0, probeLength);
                    }
                    decompressed = new LZMAInputStream(probeInput, MAX_XZ_MEMORY_KIB);
                }

                try (InputStream decompressedStream = decompressed) {
                    TarReader reader = new TarReader(decompressedStream, zip, namespace);
                    reader.readArchive();

                    // A valid tar may have zero padding after its two end
                    // blocks, but no other bytes may follow.  This also
                    // forces the lzip decoder to consume and verify every
                    // member trailer.
                    reader.drainZeroTail();
                    if (probeInput.read() != -1) {
                        throw new IOException("Trailing bytes after compressed archive");
                    }
                }
            }

            moveLocal(temporary, outputZip);
        } catch (IOException | RuntimeException e) {
            LocalIoException localFailure = findLocalIo(e);
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException cleanup) {
                if (localFailure != null) {
                    localFailure.addSuppressed(cleanup);
                    throw localFailure;
                }
                LocalIoException cleanupFailure = new LocalIoException(
                        "Failed to clean temporary translation archive", cleanup);
                cleanupFailure.addSuppressed(e);
                throw cleanupFailure;
            }
            if (localFailure != null) {
                throw localFailure;
            }
            throw e;
        }
    }

    private static LocalIoException findLocalIo(Throwable error) {
        if (error instanceof LocalIoException) {
            return (LocalIoException) error;
        }
        for (Throwable suppressed : error.getSuppressed()) {
            LocalIoException local = findLocalIo(suppressed);
            if (local != null) {
                return local;
            }
        }
        Throwable cause = error.getCause();
        return cause == null ? null : findLocalIo(cause);
    }

    private static LocalInputStream openInput(Path archive) throws IOException {
        try {
            return new LocalInputStream(new BufferedInputStream(Files.newInputStream(archive.toFile().toPath())));
        } catch (IOException e) {
            throw wrapLocal("Cannot open translation archive: " + archive, e);
        }
    }

    private static LocalOutputStream openOutput(Path output) throws IOException {
        try {
            return new LocalOutputStream(Files.newOutputStream(output));
        } catch (IOException e) {
            throw wrapLocal("Cannot create decoded translation archive: " + output, e);
        }
    }

    private static void createDirectoriesLocal(Path path) throws IOException {
        try {
            Files.createDirectories(path);
        } catch (IOException e) {
            throw wrapLocal("Cannot create decoded archive directory: " + path, e);
        }
    }

    private static void deleteLocal(Path path) throws IOException {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw wrapLocal("Cannot remove temporary translation archive: " + path, e);
        }
    }

    private static void moveLocal(Path source, Path target) throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw wrapLocal("Cannot publish decoded translation archive: " + target, e);
        }
    }

    private static LocalIoException wrapLocal(String message, IOException cause) {
        return cause instanceof LocalIoException
                ? (LocalIoException) cause : new LocalIoException(message, cause);
    }

    private static final class LocalInputStream extends FilterInputStream {
        private LocalInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            try {
                return super.read();
            } catch (IOException e) {
                throw wrapLocal("Cannot read translation archive", e);
            }
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            try {
                return super.read(bytes, offset, length);
            } catch (IOException e) {
                throw wrapLocal("Cannot read translation archive", e);
            }
        }

        @Override
        public long skip(long amount) throws IOException {
            try {
                return super.skip(amount);
            } catch (IOException e) {
                throw wrapLocal("Cannot read translation archive", e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } catch (IOException e) {
                throw wrapLocal("Cannot close translation archive", e);
            }
        }
    }

    private static final class LocalOutputStream extends FilterOutputStream {
        private LocalOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void write(int value) throws IOException {
            try {
                super.write(value);
            } catch (IOException e) {
                throw wrapLocal("Cannot write decoded translation archive", e);
            }
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            try {
                out.write(bytes, offset, length);
            } catch (IOException e) {
                throw wrapLocal("Cannot write decoded translation archive", e);
            }
        }

        @Override
        public void flush() throws IOException {
            try {
                super.flush();
            } catch (IOException e) {
                throw wrapLocal("Cannot flush decoded translation archive", e);
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } catch (IOException e) {
                throw wrapLocal("Cannot close decoded translation archive", e);
            }
        }
    }

    private static boolean hasLzipMagic(byte[] bytes) {
        return bytes[0] == LZIP_MAGIC[0]
                && bytes[1] == LZIP_MAGIC[1]
                && bytes[2] == LZIP_MAGIC[2]
                && bytes[3] == LZIP_MAGIC[3];
    }

    private static int readAtMost(InputStream input, byte[] buffer, int offset, int length)
            throws IOException {
        int total = 0;
        while (total < length) {
            int count = input.read(buffer, offset + total, length - total);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            total += count;
        }
        return total;
    }

    private static String normalizeNamespace(String namespace) throws IOException {
        if (namespace == null || namespace.isEmpty()
                || namespace.indexOf('/') >= 0 || namespace.indexOf('\\') >= 0
                || namespace.indexOf('\0') >= 0 || namespace.indexOf(':') >= 0
                || ".".equals(namespace) || "..".equals(namespace)) {
            throw new IOException("Unsafe translation namespace: " + namespace);
        }
        if (StandardCharsets.UTF_8.encode(namespace).remaining() > MAX_ZIP_NAME_BYTES) {
            throw new IOException("Translation namespace is too long");
        }
        return namespace;
    }

    private static String decodeUtf8(byte[] bytes, int offset, int length) throws IOException {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(bytes, offset, length))
                    .toString();
        } catch (CharacterCodingException e) {
            throw new IOException("Invalid UTF-8 in tar metadata", e);
        }
    }

    private static String decodeTarString(byte[] header, int offset, int length) throws IOException {
        int end = offset + length;
        int nul = offset;
        while (nul < end && header[nul] != 0) {
            ++nul;
        }
        return decodeUtf8(header, offset, nul - offset);
    }

    private static String normalizeTarPath(String path, boolean directory) throws IOException {
        if (path == null || path.isEmpty() || path.indexOf('\0') >= 0
                || path.indexOf('\\') >= 0 || path.startsWith("/")
                || path.startsWith("\\") || path.indexOf(':') >= 0) {
            throw new IOException("Unsafe tar path: " + path);
        }

        String[] pieces = path.split("/", -1);
        StringBuilder normalized = new StringBuilder(path.length());
        for (String piece : pieces) {
            if (piece.isEmpty() || ".".equals(piece)) {
                continue;
            }
            if ("..".equals(piece)) {
                throw new IOException("Traversal in tar path: " + path);
            }
            if (normalized.length() > 0) {
                normalized.append('/');
            }
            normalized.append(piece);
        }
        if (normalized.length() == 0 && !directory) {
            throw new IOException("Empty tar path: " + path);
        }
        return normalized.toString();
    }

    private static long parseTarNumber(byte[] bytes, int offset, int length, String field)
            throws IOException {
        int end = offset + length;
        if (offset < 0 || length < 0 || end > bytes.length) {
            throw new IOException("Invalid tar " + field + " field");
        }

        if ((bytes[offset] & 0x80) != 0) {
            long value = bytes[offset] & 0x7f;
            for (int i = offset + 1; i < end; ++i) {
                int next = bytes[i] & 0xff;
                if (value > (Long.MAX_VALUE - next) / 256L) {
                    throw new IOException("Tar " + field + " is too large");
                }
                value = value * 256L + next;
            }
            return value;
        }

        long value = 0;
        boolean foundDigit = false;
        boolean trailing = false;
        for (int i = offset; i < end; ++i) {
            int current = bytes[i] & 0xff;
            if (current == 0 || current == ' ') {
                if (foundDigit) {
                    trailing = true;
                }
                continue;
            }
            if (current < '0' || current > '7' || trailing) {
                throw new IOException("Invalid tar " + field + " field");
            }
            foundDigit = true;
            int digit = current - '0';
            if (value > (Long.MAX_VALUE - digit) / 8L) {
                throw new IOException("Tar " + field + " is too large");
            }
            value = value * 8L + digit;
        }
        return value;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static void requireZipNameLength(String name) throws IOException {
        if (StandardCharsets.UTF_8.encode(name).remaining() > MAX_ZIP_NAME_BYTES) {
            throw new IOException("Tar path is too long for a ZIP entry");
        }
    }

    private static void skipFully(InputStream input, long amount) throws IOException {
        if (amount < 0) {
            throw new IOException("Negative tar entry size");
        }
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        long remaining = amount;
        while (remaining > 0) {
            int requested = (int) Math.min(buffer.length, remaining);
            int count = input.read(buffer, 0, requested);
            if (count < 0) {
                throw new IOException("Truncated tar entry");
            }
            if (count == 0) {
                continue;
            }
            remaining -= count;
        }
    }

    private static void skipPadding(InputStream input, long amount) throws IOException {
        long padding = (TAR_BLOCK_SIZE - (amount % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
        skipFully(input, padding);
    }

    private static final class TarReader {
        private final InputStream input;
        private final ZipOutputStream output;
        private final String namespace;
        private final byte[] copyBuffer = new byte[COPY_BUFFER_SIZE];
        private final byte[] header = new byte[TAR_BLOCK_SIZE];
        private String longName;
        private PaxAttributes localPax = new PaxAttributes();
        private PaxAttributes globalPax = new PaxAttributes();
        private boolean reachedEnd;

        private TarReader(InputStream input, ZipOutputStream output, String namespace) {
            this.input = input;
            this.output = output;
            this.namespace = namespace;
        }

        private void readArchive() throws IOException {
            while (!reachedEnd) {
                if (!readBlock(header)) {
                    throw new IOException("Truncated tar header");
                }
                if (isZeroBlock(header)) {
                    if (!readBlock(header) || !isZeroBlock(header)) {
                        throw new IOException("Tar archive has only one end block");
                    }
                    reachedEnd = true;
                    continue;
                }
                readEntry(header);
            }
        }

        private void readEntry(byte[] entryHeader) throws IOException {
            verifyChecksum(entryHeader);
            String headerName = decodeTarString(entryHeader, 0, 100);
            String prefix = decodeTarString(entryHeader, 345, 155);
            if (!prefix.isEmpty()) {
                headerName = prefix + "/" + headerName;
            }
            long headerSize = parseTarNumber(entryHeader, 124, 12, "size");
            int type = entryHeader[156] & 0xff;

            if (type == 'L') {
                longName = readMetadataString(headerSize, "GNU long name");
                skipPadding(input, headerSize);
                return;
            }
            if (type == 'x' || type == 'g') {
                PaxAttributes attributes = readPaxAttributes(headerSize);
                skipPadding(input, headerSize);
                if (type == 'g') {
                    globalPax.merge(attributes);
                } else {
                    localPax.merge(attributes);
                }
                return;
            }
            if (type == 'K') {
                throw new IOException("GNU long link entries are not supported");
            }
            if (type == '1' || type == '2' || type == '3' || type == '4'
                    || type == '6' || type == '7') {
                throw new IOException("Tar links and device entries are not supported");
            }
            if (type != 0 && type != '0' && type != '5') {
                throw new IOException("Unsupported tar entry type: " + type);
            }

            String path = localPax.path != null ? localPax.path
                    : (globalPax.path != null ? globalPax.path : longName);
            if (path == null) {
                path = headerName;
            }
            String normalizedPath = normalizeTarPath(path, type == '5');
            long size = localPax.size != null ? localPax.size
                    : (globalPax.size != null ? globalPax.size : headerSize);
            if (size < 0) {
                throw new IOException("Negative tar entry size");
            }

            String zipName = "assets/" + namespace + "/" + normalizedPath;
            requireZipNameLength(zipName);
            if (type == '5') {
                if (size != 0) {
                    skipFully(input, size);
                }
                skipPadding(input, size);
                // TAR commonly includes "." or "./" for its root directory.
                if (!normalizedPath.isEmpty()) {
                    if (!zipName.endsWith("/")) {
                        zipName += "/";
                    }
                    requireZipNameLength(zipName);
                    output.putNextEntry(new ZipEntry(zipName));
                    output.closeEntry();
                }
            } else {
                if (path.endsWith("/")) {
                    throw new IOException("Regular tar entry has a directory path");
                }
                output.putNextEntry(new ZipEntry(zipName));
                copyEntry(size);
                output.closeEntry();
                skipPadding(input, size);
            }
            longName = null;
            localPax = new PaxAttributes();
        }

        private void copyEntry(long size) throws IOException {
            long remaining = size;
            while (remaining > 0) {
                int requested = (int) Math.min(copyBuffer.length, remaining);
                int count = input.read(copyBuffer, 0, requested);
                if (count < 0) {
                    throw new IOException("Truncated tar entry");
                }
                if (count == 0) {
                    continue;
                }
                output.write(copyBuffer, 0, count);
                remaining -= count;
            }
        }

        private String readMetadataString(long size, String description) throws IOException {
            byte[] metadata = readMetadata(size, description);
            int length = metadata.length;
            while (length > 0 && metadata[length - 1] == 0) {
                --length;
            }
            return decodeUtf8(metadata, 0, length);
        }

        private byte[] readMetadata(long size, String description) throws IOException {
            if (size < 0 || size > MAX_METADATA_SIZE) {
                throw new IOException(description + " is too large");
            }
            byte[] metadata = new byte[(int) size];
            readFully(metadata);
            return metadata;
        }

        private PaxAttributes readPaxAttributes(long size) throws IOException {
            byte[] metadata = readMetadata(size, "PAX header");
            PaxAttributes attributes = new PaxAttributes();
            int offset = 0;
            while (offset < metadata.length) {
                int space = indexOf(metadata, offset, metadata.length, (byte) ' ');
                if (space <= offset) {
                    throw new IOException("Malformed PAX record");
                }
                long recordLength = parseDecimal(metadata, offset, space);
                if (recordLength < 3 || recordLength > metadata.length - offset) {
                    throw new IOException("Malformed PAX record length");
                }
                int end = offset + (int) recordLength;
                if (metadata[end - 1] != '\n') {
                    throw new IOException("Malformed PAX record terminator");
                }
                int equals = indexOf(metadata, space + 1, end - 1, (byte) '=');
                if (equals <= space + 1) {
                    throw new IOException("Malformed PAX record");
                }
                String key = new String(metadata, space + 1, equals - space - 1, StandardCharsets.US_ASCII);
                String value = decodeUtf8(metadata, equals + 1, end - equals - 2);
                if ("path".equals(key)) {
                    attributes.path = value;
                } else if ("size".equals(key)) {
                    attributes.size = parseDecimalString(value, "PAX size");
                }
                offset = end;
            }
            return attributes;
        }

        private void drainZeroTail() throws IOException {
            if (!reachedEnd) {
                throw new IOException("Tar archive did not reach its end");
            }
            byte[] buffer = new byte[COPY_BUFFER_SIZE];
            int count;
            while ((count = input.read(buffer, 0, buffer.length)) != -1) {
                for (int i = 0; i < count; ++i) {
                    if (buffer[i] != 0) {
                        throw new IOException("Non-zero bytes after tar end blocks");
                    }
                }
            }
        }

        private boolean readBlock(byte[] block) throws IOException {
            int count = 0;
            while (count < block.length) {
                int read = input.read(block, count, block.length - count);
                if (read < 0) {
                    return count == 0 ? false : throwTruncatedBlock();
                }
                if (read == 0) {
                    continue;
                }
                count += read;
            }
            return true;
        }

        private boolean throwTruncatedBlock() throws IOException {
            throw new IOException("Truncated tar block");
        }

        private void readFully(byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Truncated tar metadata");
                }
                if (count == 0) {
                    continue;
                }
                offset += count;
            }
        }

        private static void verifyChecksum(byte[] entryHeader) throws IOException {
            long actual = parseTarNumber(entryHeader, 148, 8, "checksum");
            long sum = 0;
            for (int i = 0; i < entryHeader.length; ++i) {
                sum += i >= 148 && i < 156 ? 0x20 : entryHeader[i] & 0xff;
            }
            if (actual != sum) {
                throw new IOException("Invalid tar header checksum");
            }
        }

        private static int indexOf(byte[] bytes, int start, int end, byte needle) {
            for (int i = start; i < end; ++i) {
                if (bytes[i] == needle) {
                    return i;
                }
            }
            return -1;
        }

        private static long parseDecimal(byte[] bytes, int start, int end) throws IOException {
            long value = 0;
            if (start >= end) {
                throw new IOException("Empty decimal value");
            }
            for (int i = start; i < end; ++i) {
                int digit = bytes[i] - '0';
                if (digit < 0 || digit > 9
                        || value > (Long.MAX_VALUE - digit) / 10L) {
                    throw new IOException("Invalid decimal value");
                }
                value = value * 10L + digit;
            }
            return value;
        }

        private static long parseDecimalString(String value, String description) throws IOException {
            if (value.isEmpty()) {
                throw new IOException("Empty " + description);
            }
            long result = 0;
            for (int i = 0; i < value.length(); ++i) {
                char current = value.charAt(i);
                if (current < '0' || current > '9'
                        || result > (Long.MAX_VALUE - (current - '0')) / 10L) {
                    throw new IOException("Invalid " + description);
                }
                result = result * 10L + current - '0';
            }
            return result;
        }
    }

    private static final class PaxAttributes {
        private String path;
        private Long size;

        private void merge(PaxAttributes other) {
            if (other.path != null) {
                path = other.path;
            }
            if (other.size != null) {
                size = other.size;
            }
        }
    }

    private static final class CountingInputStream extends FilterInputStream {
        private long count;

        private CountingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public int read() throws IOException {
            int value = super.read();
            if (value >= 0) {
                increment(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = super.read(bytes, offset, length);
            if (read > 0) {
                increment(read);
            }
            return read;
        }

        private void increment(long amount) throws IOException {
            if (Long.MAX_VALUE - count < amount) {
                throw new IOException("Compressed archive is too large");
            }
            count += amount;
        }

        private long getCount() {
            return count;
        }
    }

    private static final class LzipInputStream extends InputStream {
        private final InputStream input;
        private final byte[] singleByte = new byte[1];
        private final CountingInputStream counted;
        private byte[] firstHeader;
        private LZMAInputStream decoder;
        private long memberStart;
        private long memberOutputSize;
        private CRC32 memberCrc;
        private boolean finished;

        private LzipInputStream(InputStream input, CountingInputStream counted, byte[] firstHeader) {
            this.input = input;
            this.counted = counted;
            this.firstHeader = Arrays.copyOf(firstHeader, firstHeader.length);
        }

        @Override
        public int read() throws IOException {
            return read(singleByte, 0, 1) < 0 ? -1 : singleByte[0] & 0xff;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            if (bytes == null) {
                throw new NullPointerException("bytes");
            }
            if (offset < 0 || length < 0 || offset > bytes.length - length) {
                throw new IndexOutOfBoundsException();
            }
            if (length == 0) {
                return 0;
            }
            if (finished) {
                return -1;
            }

            while (true) {
                if (decoder == null) {
                    if (!startMember()) {
                        return -1;
                    }
                }
                int count = decoder.read(bytes, offset, length);
                if (count > 0) {
                    memberCrc.update(bytes, offset, count);
                    if (Long.MAX_VALUE - memberOutputSize < count) {
                        throw new IOException("Lzip member is too large");
                    }
                    memberOutputSize += count;
                    return count;
                }
                finishMember();
            }
        }

        private boolean startMember() throws IOException {
            byte[] header = firstHeader;
            if (header != null) {
                firstHeader = null;
                memberStart = counted.getCount() - LZIP_HEADER_SIZE;
            } else {
                memberStart = counted.getCount();
                header = new byte[LZIP_HEADER_SIZE];
                int count = readAtMost(input, header, 0, header.length);
                if (count == 0) {
                    finished = true;
                    return false;
                }
                if (count != header.length) {
                    throw new IOException("Truncated lzip header");
                }
            }
            validateHeader(header);
            int dictionarySize = getDictionarySize(header[5] & 0xff);
            if (LZMAInputStream.getMemoryUsage(dictionarySize, (byte) 0x5d) > MAX_XZ_MEMORY_KIB) {
                throw new IOException("Lzip dictionary exceeds memory limit");
            }
            decoder = new LZMAInputStream(input, -1L, (byte) 0x5d, dictionarySize);
            memberOutputSize = 0;
            memberCrc = new CRC32();
            return true;
        }

        private void finishMember() throws IOException {
            byte[] trailer = new byte[LZIP_TRAILER_SIZE];
            readFully(input, trailer);
            long expectedCrc = readLe32(trailer, 0);
            long expectedDataSize = readLe64(trailer, 4);
            long expectedMemberSize = readLe64(trailer, 12);
            long actualMemberSize = counted.getCount() - memberStart;
            if (expectedCrc != memberCrc.getValue()) {
                throw new IOException("Invalid lzip member CRC");
            }
            if (expectedDataSize != memberOutputSize) {
                throw new IOException("Invalid lzip uncompressed size");
            }
            if (expectedMemberSize != actualMemberSize || expectedMemberSize < 26) {
                throw new IOException("Invalid lzip member size");
            }
            decoder = null;
            memberCrc = null;
        }

        private static void validateHeader(byte[] header) throws IOException {
            if (!hasLzipMagic(header) || header[4] != 1) {
                throw new IOException("Invalid lzip header");
            }
            int dictionaryCode = header[5] & 0xff;
            int exponent = dictionaryCode & 0x1f;
            if (exponent < 12 || exponent > 29) {
                throw new IOException("Invalid lzip dictionary size");
            }
            long dictionarySize = getDictionarySize(dictionaryCode);
            if (dictionarySize < 4096 || dictionarySize > MAX_LZIP_DICTIONARY_SIZE) {
                throw new IOException("Invalid lzip dictionary size");
            }
        }

        private static int getDictionarySize(int dictionaryCode) {
            int exponent = dictionaryCode & 0x1f;
            int fraction = dictionaryCode >>> 5;
            long dictionarySize = 1L << exponent;
            dictionarySize -= dictionarySize / 16L * fraction;
            if (dictionarySize > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Lzip dictionary is too large");
            }
            return (int) dictionarySize;
        }

        private static long readLe32(byte[] bytes, int offset) {
            return (bytes[offset] & 0xffL)
                    | ((bytes[offset + 1] & 0xffL) << 8)
                    | ((bytes[offset + 2] & 0xffL) << 16)
                    | ((bytes[offset + 3] & 0xffL) << 24);
        }

        private static long readLe64(byte[] bytes, int offset) throws IOException {
            long value = 0;
            for (int i = 7; i >= 0; --i) {
                int next = bytes[offset + i] & 0xff;
                if (value > (Long.MAX_VALUE - next) / 256L) {
                    throw new IOException("Lzip value is too large");
                }
                value = value * 256L + next;
            }
            return value;
        }

        private static void readFully(InputStream input, byte[] bytes) throws IOException {
            int offset = 0;
            while (offset < bytes.length) {
                int count = input.read(bytes, offset, bytes.length - offset);
                if (count < 0) {
                    throw new IOException("Truncated lzip trailer");
                }
                if (count == 0) {
                    continue;
                }
                offset += count;
            }
        }

        @Override
        public void close() throws IOException {
            if (decoder != null) {
                decoder.close();
                decoder = null;
            }
            input.close();
            finished = true;
        }
    }
}
