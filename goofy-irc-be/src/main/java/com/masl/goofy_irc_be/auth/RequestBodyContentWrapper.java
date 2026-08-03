package com.masl.goofy_irc_be.auth;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import org.jspecify.annotations.NonNull;
import org.springframework.web.util.ContentCachingRequestWrapper;

import java.io.ByteArrayInputStream;
import java.io.IOException;

// https://stackoverflow.com/questions/63791808/required-request-body-is-missing-after-using-contentcachingrequestwrapper
public class RequestBodyContentWrapper extends ContentCachingRequestWrapper {

    private final ContentCachingRequestWrapper wrapped;
    private final RequestBodyInputStreamWrapper bodyProcessorIS;

    public RequestBodyContentWrapper(ContentCachingRequestWrapper wrapped, int cacheSize) throws IOException {
        super(wrapped, cacheSize);
        this.wrapped = wrapped;
        this.bodyProcessorIS = new RequestBodyInputStreamWrapper(super.getInputStream());
    }

    @Override
    public @NonNull ServletInputStream getInputStream() {
        return bodyProcessorIS;
    }

    // use the method supported by ContentCachingRequestWrapper to read the bytes
    public void prepareInputStream() {
        bodyProcessorIS.resetReading(wrapped.getContentAsByteArray());
    }

    // ---- inner wrapper ----

    public static class RequestBodyInputStreamWrapper extends ServletInputStream {

        private final ServletInputStream wrapped;
        private ByteArrayInputStream bais;

        public RequestBodyInputStreamWrapper(ServletInputStream wrapped) {
            this.wrapped = wrapped;
        }

        // convert the bytes read from the cache to InputStream
        // which later can be used in the read()
        public void resetReading(byte[] bytes) {
            this.bais = new ByteArrayInputStream(bytes);
        }

        @Override
        public int read() {
            return bais.read();
        }

        @Override
        public boolean isFinished() {
            return wrapped.isFinished();
        }

        @Override
        public boolean isReady() {
            return wrapped.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            wrapped.setReadListener(readListener);
        }
    }
}