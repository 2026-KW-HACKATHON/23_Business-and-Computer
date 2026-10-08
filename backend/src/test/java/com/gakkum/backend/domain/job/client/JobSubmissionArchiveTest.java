package com.gakkum.backend.domain.job.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gakkum.backend.global.exception.BusinessException;
import com.gakkum.backend.global.exception.DownloadAbortedException;
import com.gakkum.backend.global.exception.ErrorCode;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

class JobSubmissionArchiveTest {

    private static final byte[] LARGE_CONTENT = randomBytes(200 * 1024);

    private final JobSubmissionFileStorageClient storageClient = mock(JobSubmissionFileStorageClient.class);
    private final AtomicInteger slotReleases = new AtomicInteger();
    private final List<TrackedStream> streams = new ArrayList<>();

    @Test
    @DisplayName("요청한 순서대로 의뢰 폴더와 의뢰 안 순번을 붙여 쓰고, 같은 이름의 파일과 한글 파일명도 원본 그대로 담는다")
    void writesFilesInRequestOrderWithJobFolderAndSequence() throws IOException {
        givenObject("job-submissions/42/7/a/시안.png", "초안".getBytes(StandardCharsets.UTF_8));
        givenObject("job-submissions/42/7/b/시안.png", LARGE_CONTENT);
        givenObject("job-submissions/43/7/c/메뉴판 문서 (최종).pdf", "메뉴판".getBytes(StandardCharsets.UTF_8));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        archive(file(42L, "job-submissions/42/7/a/시안.png"),
                file(43L, "job-submissions/43/7/c/메뉴판 문서 (최종).pdf"),
                file(42L, "job-submissions/42/7/b/시안.png")).writeTo(output);

        Map<String, byte[]> entries = unzip(output.toByteArray());
        assertThat(entries.keySet()).containsExactly(
                "job-42/1_시안.png", "job-43/1_메뉴판 문서 (최종).pdf", "job-42/2_시안.png");
        assertThat(entries.get("job-42/1_시안.png")).isEqualTo("초안".getBytes(StandardCharsets.UTF_8));
        assertThat(entries.get("job-43/1_메뉴판 문서 (최종).pdf")).isEqualTo("메뉴판".getBytes(StandardCharsets.UTF_8));
        assertThat(entries.get("job-42/2_시안.png")).isEqualTo(LARGE_CONTENT);
        assertThat(streams).allSatisfy(stream -> {
            assertThat(stream.closed).isTrue();
            assertThat(stream.aborted).isFalse();
        });
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("ZIP 항목 이름은 파일명에서 경로 구분자와 제어 문자를 빼고, 남는 이름이 없으면 file로 둔다")
    void sanitizesEntryName() {
        Map<String, String> expectedByFileName = new LinkedHashMap<>();
        expectedByFileName.put("draft.pdf", "job-42/3_draft.pdf");
        expectedByFileName.put("..\\..\\evil.sh", "job-42/3_....evil.sh");
        expectedByFileName.put("C:\\temp\\a.png", "job-42/3_C:tempa.png");
        expectedByFileName.put("줄\n바꿈\t탭.txt", "job-42/3_줄바꿈탭.txt");
        expectedByFileName.put("\0\u001f\u007f", "job-42/3_file");
        expectedByFileName.put(" ", "job-42/3_file");

        expectedByFileName.forEach((fileName, expected) -> assertThat(
                JobSubmissionArchive.entryName(42L, 3, "job-submissions/42/7/a/" + fileName)).isEqualTo(expected));
    }

    @Test
    @DisplayName("전송 중에 저장소 읽기가 실패하면 JOB_SUBMISSION_502로 중단하고, ZIP 끝 레코드를 쓰지 않으며 읽던 스트림은 끊고 슬롯을 돌려준다")
    void abortsWithoutFinishingZipWhenStorageReadFails() {
        givenObject("job-submissions/42/7/a/first.bin", LARGE_CONTENT);
        givenStream("job-submissions/42/7/b/second.bin", new InputStream() {
            private int remaining = 1024;

            @Override
            public int read() throws IOException {
                if (remaining-- > 0) {
                    return 7;
                }
                throw new IOException("connection reset");
            }
        });
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThatThrownBy(() -> archive(file(42L, "job-submissions/42/7/a/first.bin"),
                file(42L, "job-submissions/42/7/b/second.bin")).writeTo(output))
                .isInstanceOfSatisfying(DownloadAbortedException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.JOB_SUBMISSION_FILE_UNAVAILABLE);
                    assertThat(exception).hasRootCauseMessage("connection reset");
                });

        assertThat(output.size()).isGreaterThan(LARGE_CONTENT.length);
        assertThat(new String(output.toByteArray(), StandardCharsets.ISO_8859_1)).doesNotContain("PK\u0005\u0006");
        assertThat(streams.get(0).closed).isTrue();
        assertThat(streams.get(0).aborted).isFalse();
        assertThat(streams.get(1).aborted).isTrue();
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("첫 파일을 열지 못하면 아무것도 쓰지 않고 저장소 클라이언트의 에러 코드로 중단하며 슬롯을 돌려준다")
    void abortsBeforeWritingWhenFirstFileCannotBeOpened() {
        when(storageClient.open("job-submissions/42/7/a/missing.pdf"))
                .thenThrow(new BusinessException(ErrorCode.JOB_SUBMISSION_FILE_NOT_FOUND));
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThatThrownBy(() -> archive(file(42L, "job-submissions/42/7/a/missing.pdf")).writeTo(output))
                .isInstanceOfSatisfying(DownloadAbortedException.class, exception -> assertThat(
                        exception.getErrorCode()).isEqualTo(ErrorCode.JOB_SUBMISSION_FILE_NOT_FOUND));

        assertThat(output.size()).isZero();
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("받는 쪽이 끊어 응답에 쓰지 못하면 쓰기 실패를 그대로 던지고, 읽던 스트림은 끊고 슬롯을 돌려준다")
    void stopsAndReleasesWhenResponseWriteFails() {
        givenObject("job-submissions/42/7/a/first.bin", LARGE_CONTENT);
        OutputStream disconnected = new OutputStream() {
            private int written;

            @Override
            public void write(int value) throws IOException {
                if (++written > 4096) {
                    throw new IOException("Broken pipe");
                }
            }
        };

        assertThatThrownBy(() -> archive(file(42L, "job-submissions/42/7/a/first.bin")).writeTo(disconnected))
                .isExactlyInstanceOf(IOException.class)
                .hasMessage("Broken pipe");

        assertThat(streams.get(0).aborted).isTrue();
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("인터럽트로 끝나지 않는 읽기 중에 취소하면 스트림을 끊고, 슬롯은 전송 작업이 끝난 뒤에야 돌려준다")
    void cancelAbortsBlockedReadAndReleasesSlotAfterTaskEnds() throws Exception {
        CountDownLatch reading = new CountDownLatch(1);
        CountDownLatch aborted = new CountDownLatch(1);
        CountDownLatch taskMayEnd = new CountDownLatch(1);
        InputStream blocked = new InputStream() {
            @Override
            public int read() throws IOException {
                reading.countDown();
                awaitIgnoringInterrupts(aborted);
                awaitIgnoringInterrupts(taskMayEnd);
                throw new IOException("stream aborted");
            }
        };
        when(storageClient.open("job-submissions/42/7/a/slow.bin")).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(blocked, aborted::countDown)));
        JobSubmissionArchive archive = archive(file(42L, "job-submissions/42/7/a/slow.bin"));
        CompletableFuture<Throwable> task = new CompletableFuture<>();
        Thread writer = new Thread(() -> {
            try {
                archive.writeTo(new ByteArrayOutputStream());
                task.complete(null);
            } catch (Throwable failure) {
                task.complete(failure);
            }
        });
        writer.start();
        assertThat(reading.await(5, TimeUnit.SECONDS)).isTrue();

        writer.interrupt();
        archive.cancel();

        assertThat(aborted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(task).isNotDone();
        assertThat(slotReleases).hasValue(0);

        taskMayEnd.countDown();
        assertThat(task.get(5, TimeUnit.SECONDS)).isInstanceOf(DownloadAbortedException.class);
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("전송을 시작하기 전에 취소하면 슬롯을 바로 돌려주고, 뒤늦은 전송은 저장소를 읽지 않고 거부한다")
    void cancelBeforeStartReleasesSlotAndRejectsLateTransfer() {
        JobSubmissionArchive archive = archive(file(42L, "job-submissions/42/7/a/draft.pdf"));

        archive.cancel();

        assertThat(slotReleases).hasValue(1);
        assertThatThrownBy(() -> archive.writeTo(new ByteArrayOutputStream()))
                .isInstanceOf(DownloadAbortedException.class);
        verifyNoInteractions(storageClient);
        assertThat(slotReleases).hasValue(1);
    }

    @Test
    @DisplayName("전송을 마친 뒤 취소하거나 다시 close 해도 슬롯은 한 번만 돌려준다")
    void releasesSlotOnlyOnce() throws IOException {
        givenObject("job-submissions/42/7/a/draft.pdf", new byte[] { 1 });
        JobSubmissionArchive archive = archive(file(42L, "job-submissions/42/7/a/draft.pdf"));

        archive.writeTo(new ByteArrayOutputStream());
        archive.cancel();
        archive.close();
        archive.close();

        assertThat(slotReleases).hasValue(1);
    }

    private JobSubmissionArchive archive(JobSubmissionArchive.File... files) {
        return new JobSubmissionArchive(storageClient, List.of(files), slotReleases::incrementAndGet);
    }

    private static JobSubmissionArchive.File file(Long jobId, String key) {
        return new JobSubmissionArchive.File(jobId, key);
    }

    private void givenObject(String key, byte[] content) {
        givenStream(key, new ByteArrayInputStream(content));
    }

    private void givenStream(String key, InputStream content) {
        TrackedStream stream = new TrackedStream(content);
        streams.add(stream);
        when(storageClient.open(key)).thenReturn(new ResponseInputStream<>(
                GetObjectResponse.builder().build(), AbortableInputStream.create(stream, () -> stream.aborted = true)));
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
            for (ZipEntry entry = input.getNextEntry(); entry != null; entry = input.getNextEntry()) {
                entries.put(entry.getName(), input.readAllBytes());
            }
        }
        return entries;
    }

    private static void awaitIgnoringInterrupts(CountDownLatch latch) {
        while (true) {
            try {
                latch.await();
                return;
            } catch (InterruptedException ignored) {
                // 블로킹 소켓 읽기처럼 인터럽트로는 깨어나지 않는다
            }
        }
    }

    private static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new Random(7).nextBytes(bytes);
        return bytes;
    }

    /** 저장소 스트림을 끝까지 읽고 닫았는지, 중간에 끊었는지 기록한다. */
    private static final class TrackedStream extends InputStream {

        private final InputStream delegate;
        private boolean closed;
        private boolean aborted;

        private TrackedStream(InputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            delegate.close();
        }
    }
}
