package com.gakkum.backend.domain.job.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.DownloadAbortedException;
import com.gakkum.backend.global.exception.ErrorCode;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

/**
 * 고른 작업물 파일을 저장소에서 하나씩 읽어 ZIP으로 내보내는 다운로드 한 건. 파일 전체나 완성된 ZIP을 메모리에 두지 않는다.
 * 처리 슬롯 하나를 쥐고 있고, 전송을 시작했으면 전송 작업이 실제로 끝났을 때 돌려준다.
 * 요청이 먼저 끝나면(타임아웃·연결 끊김) cancel로 읽던 저장소 스트림을 끊어 작업을 끝내고, 전송을 시작하지 못하면 close로 돌려줘야 한다.
 */
@Slf4j
public class JobSubmissionArchive implements AutoCloseable {

    private static final int BUFFER_SIZE = 16 * 1024;
    private static final String UNNAMED_FILE = "file";

    private final JobSubmissionFileStorageClient storageClient;
    private final List<File> files;
    private final Runnable slotRelease;
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Object lock = new Object();
    private boolean started;
    private boolean cancelled;
    private ResponseInputStream<GetObjectResponse> reading;

    JobSubmissionArchive(JobSubmissionFileStorageClient storageClient, List<File> files, Runnable slotRelease) {
        this.storageClient = storageClient;
        this.files = List.copyOf(files);
        this.slotRelease = slotRelease;
    }

    /**
     * 파일을 요청 순서대로 job-{jobId}/{의뢰 안 순번}_{원본 파일명} 항목으로 쓴다.
     * 저장소에서 읽지 못하면 DownloadAbortedException, 응답에 쓰지 못하면(받는 쪽이 끊음) IOException을 던지고,
     * 어느 쪽이든 ZIP 끝 레코드를 쓰지 않아 중간에 끊긴 파일이 정상 ZIP으로 열리지 않게 한다.
     */
    public void writeTo(OutputStream output) throws IOException {
        long startedAt = System.nanoTime();
        ArchiveOutputStream zip = new ArchiveOutputStream(output);
        try {
            synchronized (lock) {
                if (cancelled) {
                    throw new DownloadAbortedException(ErrorCode.JOB_SUBMISSION_DOWNLOAD_BUSY, null);
                }
                started = true;
            }
            Map<Long, Integer> sequences = new HashMap<>();
            for (File file : files) {
                int sequence = sequences.merge(file.jobId(), 1, Integer::sum);
                write(zip, entryName(file.jobId(), sequence, file.key()), file.key());
            }
            zip.finish();
            log.info("Job submission archive sent: files={}, elapsed={}ms", files.size(), elapsedMillis(startedAt));
        } catch (IOException exception) {
            log.info("Job submission archive stopped while writing the response: files={}, elapsed={}ms, cause={}",
                    files.size(), elapsedMillis(startedAt), exception.toString());
            throw exception;
        } catch (DownloadAbortedException exception) {
            log.warn("Job submission archive aborted: files={}, elapsed={}ms, cause={}",
                    files.size(), elapsedMillis(startedAt), String.valueOf(exception.getCause()));
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Job submission archive failed: files={}, elapsed={}ms",
                    files.size(), elapsedMillis(startedAt), exception);
            throw new DownloadAbortedException(ErrorCode.INTERNAL_SERVER_ERROR, exception);
        } finally {
            zip.release();
            close();
        }
    }

    /**
     * 요청이 끝났을 때 부른다. 읽던 저장소 스트림을 끊어, 인터럽트로 깨어나지 않는 읽기에 묶인 전송 작업도 끝나게 한다.
     * 슬롯은 전송 작업이 끝나면서 돌려주므로 작업이 남아 있는 동안 새 다운로드가 그 자리를 쓰지 못한다.
     * 전송을 시작하지 않았으면 바로 돌려주고, 이후의 전송 시도는 거부한다.
     */
    public void cancel() {
        ResponseInputStream<GetObjectResponse> content;
        boolean neverStarted;
        synchronized (lock) {
            cancelled = true;
            content = reading;
            neverStarted = !started;
        }
        if (content != null) {
            content.abort();
        }
        if (neverStarted) {
            close();
        }
    }

    /** 처리 슬롯을 돌려준다. 여러 번 불러도 한 번만 돌려준다. */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            slotRelease.run();
        }
    }

    private void write(ZipOutputStream zip, String entryName, String key) throws IOException {
        // 첫 파일을 열지 못했을 때 아직 아무것도 보내지 않아 오류 응답을 줄 수 있도록, 항목을 쓰기 전에 연다
        ResponseInputStream<GetObjectResponse> content = open(key);
        boolean copied = false;
        try {
            startReading(content);
            zip.putNextEntry(new ZipEntry(entryName));
            copy(content, zip);
            zip.closeEntry();
            copied = true;
        } finally {
            synchronized (lock) {
                reading = null;
            }
            // 다 읽지 않은 스트림을 close 하면 남은 본문을 끝까지 받으므로 abort로 연결을 끊는다
            if (copied) {
                content.close();
            } else {
                content.abort();
            }
        }
    }

    /** 취소가 끊을 수 있게 읽는 스트림을 알린다. 이미 취소됐으면 읽지 않는다. */
    private void startReading(ResponseInputStream<GetObjectResponse> content) {
        synchronized (lock) {
            if (cancelled) {
                throw new DownloadAbortedException(ErrorCode.JOB_SUBMISSION_DOWNLOAD_BUSY, null);
            }
            reading = content;
        }
    }

    private ResponseInputStream<GetObjectResponse> open(String key) {
        try {
            return storageClient.open(key);
        } catch (BusinessException exception) {
            throw new DownloadAbortedException(exception.getErrorCode(), exception);
        }
    }

    /** 저장소 읽기 실패와 응답 쓰기 실패를 구분하려고 직접 복사한다. */
    private void copy(InputStream content, OutputStream target) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        while (true) {
            int read;
            try {
                read = content.read(buffer);
            } catch (IOException | RuntimeException exception) {
                throw new DownloadAbortedException(ErrorCode.JOB_SUBMISSION_FILE_UNAVAILABLE, exception);
            }
            if (read < 0) {
                return;
            }
            target.write(buffer, 0, read);
        }
    }

    /** 순번을 앞에 붙여 같은 이름의 파일이 겹치지 않게 하고, 파일명에서 경로 구분자와 제어 문자를 뺀다. */
    static String entryName(Long jobId, int sequence, String key) {
        String fileName = key.substring(key.lastIndexOf('/') + 1).codePoints()
                .filter(codePoint -> codePoint != '/' && codePoint != '\\' && !Character.isISOControl(codePoint))
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
        return "job-" + jobId + "/" + sequence + "_" + (fileName.isBlank() ? UNNAMED_FILE : fileName);
    }

    private static long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    /** 저장소 키는 발급한 공개 URL에서 되돌린 값이어야 한다. */
    public record File(Long jobId, String key) {
    }

    /**
     * close 없이 쓰는 ZIP 출력. close는 실패한 전송에도 끝 레코드를 써 정상 ZIP처럼 보이게 하고 응답 스트림까지 닫으므로,
     * 성공했을 때만 finish로 끝 레코드를 쓰고 압축기는 release로 따로 돌려준다.
     */
    private static final class ArchiveOutputStream extends ZipOutputStream {

        ArchiveOutputStream(OutputStream output) {
            super(output);
            // 이미 압축된 이미지·PDF가 대부분이라 압축률보다 속도를 택한다
            setLevel(Deflater.BEST_SPEED);
        }

        void release() {
            def.end();
        }
    }
}
