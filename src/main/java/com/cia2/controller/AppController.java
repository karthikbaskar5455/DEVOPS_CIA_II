package com.cia2.controller;

import java.net.InetAddress;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Intentionally simple controller.
 *  GET /          -> HTML home page
 *  GET /api/info  -> JSON with app name, version and hostname
 *
 * The "version" comes from the APP_VERSION environment variable, set during the
 * pipeline build on the Docker image / Kubernetes Deployment. This lets us
 * prove that a code change that reaches production is visible at the endpoint.
 */
@RestController
public class AppController {

    @Value("${app.name:CIA2-Java-App}")
    private String appName;

    @Value("${app.version:local-dev}")
    private String appVersion;

    private String hostname() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return System.getenv().getOrDefault("HOSTNAME", "unknown");
        }
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        return "<!DOCTYPE html>"
                + "<html><head><meta charset='utf-8'><title>" + appName + "</title>"
                + "<style>body{font-family:system-ui,Segoe UI,Arial,sans-serif;max-width:760px;margin:60px auto;padding:0 24px;color:#222;line-height:1.6}"
                + "h1{border-bottom:3px solid #0078d4;padding-bottom:10px}code{background:#f4f4f4;padding:2px 6px;border-radius:4px;font-size:.95em}"
                + ".pill{display:inline-block;background:#0078d4;color:#fff;border-radius:20px;padding:4px 14px;font-size:.85em;margin-right:6px}"
                + ".box{background:#fafafa;border:1px solid #e0e0e0;border-radius:8px;padding:14px 18px;margin-top:24px}"
                + "</style></head><body>"
                + "<h1>" + appName + "</h1>"
                + "<p><span class='pill'>Deployed</span> <strong>Version:</strong> <code>" + appVersion + "</code></p>"
                + "<div class='box'><p><b>Host:</b> " + hostname() + "</p>"
                + "<p><b>Time:</b> " + OffsetDateTime.now().toString() + "</p>"
                + "<p>JSON status: <a href='/api/info'>/api/info</a></p></div>"
                + "</body></html>";
    }

    @GetMapping(value = "/api/info", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> info() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("app", appName);
        body.put("version", appVersion);
        body.put("hostname", hostname());
        body.put("timestamp", OffsetDateTime.now().toString());
        return body;
    }
}
