package com.filemanager.util;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Component
public class RequestInfoProvider {
    public HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes sra) {
            return sra.getRequest();
        }
        return null;
    }

    public String getClientIp() {
        HttpServletRequest req = getCurrentRequest();
        if (req == null) return "SYSTEM";
        String ip = headerFirstNonEmpty(req,
                "X-Forwarded-For",
                "X-Real-IP",
                "CF-Connecting-IP");
        if (ip == null || ip.isBlank()) {
            ip = req.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "UNKNOWN" : ip;
    }

    public String getUserAgent() {
        HttpServletRequest req = getCurrentRequest();
        if (req == null) return "SYSTEM";
        String ua = req.getHeader("User-Agent");
        return ua == null ? "UNKNOWN" : ua;
    }

    private String headerFirstNonEmpty(HttpServletRequest req, String... names) {
        for (String n : names) {
            String v = req.getHeader(n);
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }
}

