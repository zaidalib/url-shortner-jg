package com.jgu.urlshortener;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

@Controller
public class UrlController {

    private final UrlService urlService;

    public UrlController(UrlService urlService) {
        this.urlService = urlService;
    }

    // -------------------------------------------------------------------------
    // 1. GET / — home page; also handles ?c=code as a redirect shortcut
    // -------------------------------------------------------------------------
    @GetMapping("/")
    public String home(@RequestParam(name = "c", required = false) String code,
                       Model model) {
        if (code != null && !code.isBlank()) {
            // Treat ?c=code as a redirect (same logic as /{code})
            try {
                String target = urlService.resolve(code);
                return "redirect:" + target;
            } catch (LinkException e) {
                model.addAttribute("error", e.getMessage());
            }
        }
        model.addAttribute("recentLinks", urlService.recentLinks());
        return "home";
    }

    // -------------------------------------------------------------------------
    // 2. POST /shorten — create short URL, redirect to result page
    // -------------------------------------------------------------------------
    @PostMapping("/shorten")
    public String shorten(@RequestParam("url") String originalUrl,
                          @RequestParam(name = "alias", required = false) String alias,
                          @RequestParam(name = "expiry", required = false) Integer expiryDays,
                          RedirectAttributes redirectAttrs,
                          Model model) {
        try {
            UrlMapping mapping = urlService.shorten(originalUrl, alias, expiryDays);
            return "redirect:/result/" + mapping.getCode();
        } catch (LinkException e) {
            // Re-show the home page with an inline error
            redirectAttrs.addFlashAttribute("error", e.getMessage());
            redirectAttrs.addFlashAttribute("inputUrl", originalUrl);
            redirectAttrs.addFlashAttribute("inputAlias", alias);
            return "redirect:/";
        }
    }

    // -------------------------------------------------------------------------
    // 3. GET /result/{code} — display the created short URL
    // -------------------------------------------------------------------------
    @GetMapping("/result/{code}")
    public String result(@PathVariable String code,
                         HttpServletRequest request,
                         Model model) {
        try {
            UrlMapping mapping = urlService.findByCode(code);
            String shortUrl = buildBaseUrl(request) + "/" + code;
            model.addAttribute("mapping", mapping);
            model.addAttribute("shortUrl", shortUrl);
            return "result";
        } catch (LinkException e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    // -------------------------------------------------------------------------
    // 4. GET /stats/{code} — statistics for a short link
    // -------------------------------------------------------------------------
    @GetMapping("/stats/{code}")
    public String stats(@PathVariable String code, Model model) {
        try {
            UrlMapping mapping = urlService.findByCode(code);
            model.addAttribute("mapping", mapping);
            return "stats";
        } catch (LinkException e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    // -------------------------------------------------------------------------
    // 5. GET /qr/{code}.png — generate QR code image via ZXing
    // -------------------------------------------------------------------------
    @GetMapping("/qr/{code}.png")
    public ResponseEntity<byte[]> qrCode(@PathVariable String code,
                                         HttpServletRequest request) {
        try {
            urlService.findByCode(code); // 404 if unknown code

            String shortUrl = buildBaseUrl(request) + "/" + code;

            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(shortUrl, BarcodeFormat.QR_CODE, 200, 200, hints);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            byte[] pngBytes = baos.toByteArray();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("public, max-age=86400");
            return new ResponseEntity<>(pngBytes, headers, HttpStatus.OK);

        } catch (LinkException e) {
            return ResponseEntity.notFound().build();
        } catch (WriterException | IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // -------------------------------------------------------------------------
    // 6. GET /{code} — redirect to original URL, record click
    //    Regex ensures this only matches valid short codes (letters, digits, -, _)
    //    and does NOT intercept /shorten, /stats, /result, /qr, etc.
    // -------------------------------------------------------------------------
    @GetMapping("/{code:[a-zA-Z0-9_-]+}")
    public String redirect(@PathVariable String code, Model model) {
        try {
            String target = urlService.resolve(code);
            return "redirect:" + target;
        } catch (LinkException e) {
            model.addAttribute("error", e.getMessage());
            return "error";
        }
    }

    // -------------------------------------------------------------------------
    // Helper: derive the application base URL from the incoming request
    // -------------------------------------------------------------------------
    private String buildBaseUrl(HttpServletRequest request) {
        String scheme = request.getScheme();
        String host = request.getServerName();
        int port = request.getServerPort();

        // Respect X-Forwarded-Proto set by reverse proxies (Render, etc.)
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            scheme = forwardedProto.split(",")[0].trim();
        }

        boolean standardPort = ("http".equals(scheme) && port == 80)
                || ("https".equals(scheme) && port == 443);

        return standardPort
                ? scheme + "://" + host
                : scheme + "://" + host + ":" + port;
    }
}
