package com.main.suwoninfo.jwt;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;

public class CachingBodyRequestWrapper extends HttpServletRequestWrapper {

    private byte[] body;
    private Map<String,String> headers;

    public CachingBodyRequestWrapper(HttpServletRequest request) {
        super(request);
        cacheInputStream(request);
    }

    public CachingBodyRequestWrapper(HttpServletRequest req, Map<String,String> extra) {
        super(req);
        this.headers = new HashMap<>(extra);
    }

    private void cacheInputStream(HttpServletRequest request) {

        try {
            body = StreamUtils.copyToByteArray(request.getInputStream());
        } catch (IOException e) {
            body = new byte[0];
        }
    }


    @Override
    public String getHeader(String name) {
        String v = headers.get(name);
        if (v != null) return v;
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaderNames() {
        List<String> names = Collections.list(super.getHeaderNames());
        for (String k : headers.keySet()) if (!names.contains(k)) names.add(k);
        return Collections.enumeration(names);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        List<String> list = new ArrayList<>();
        if (headers.containsKey(name)) list.add(headers.get(name));
        Enumeration<String> e = super.getHeaders(name);
        while (e != null && e.hasMoreElements()) list.add(e.nextElement());
        return Collections.enumeration(list);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        return new CachedServletInputStream(body);
    }

    private static class CachedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream inputStream;

        public CachedServletInputStream(byte[] cachedBody) {
            this.inputStream = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public int read() throws IOException {
            return inputStream.read();
        }

        @Override
        public boolean isFinished() {
            return inputStream.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }
    }
}
