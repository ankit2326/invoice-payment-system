package dodopay.invoice.controller;

import dodopay.invoice.dto.PspChargeRequest;
import dodopay.invoice.dto.PspChargeResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock Payment Service Provider.
 *
 * Determines outcomes based on the card_token in the request:
 *   tok_success           -> succeeded after ~100ms
 *   tok_insufficient_funds -> failed (insufficient_funds) after ~100ms
 *   tok_card_declined      -> failed (card_declined) after ~100ms
 *   tok_timeout            -> sleeps 30 seconds then returns success
 *   tok_network_error      -> returns 500
 *
 * Supports idempotency: if the same idempotency_key is sent twice,
 * returns the same response without re-processing.
 */
@RestController
@RequestMapping("/psp")
@Profile("psp")
public class MockPspController {

    private static final Logger log = LoggerFactory.getLogger(MockPspController.class);

    // In-memory idempotency cache for the mock PSP
    private final Map<String, PspChargeResponse> idempotencyCache = new ConcurrentHashMap<>();

    @PostMapping("/charge")
    public ResponseEntity<?> charge(@RequestBody PspChargeRequest request) {
        log.info("PSP charge request: token={}, amount={}, idempotencyKey={}",
                request.cardToken(), request.amountCents(), request.idempotencyKey());

        // Check idempotency
        if (request.idempotencyKey() != null) {
            PspChargeResponse cached = idempotencyCache.get(request.idempotencyKey());
            if (cached != null) {
                log.info("PSP returning cached response for idempotency key: {}", request.idempotencyKey());
                return ResponseEntity.ok(cached);
            }
        }

        String token = request.cardToken();
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "card_token is required"));
        }

        return switch (token) {
            case "tok_success" -> {
                sleep(100);
                PspChargeResponse resp = new PspChargeResponse("succeeded", UUID.randomUUID().toString(), null);
                cacheIfKeyed(request.idempotencyKey(), resp);
                yield ResponseEntity.ok(resp);
            }
            case "tok_insufficient_funds" -> {
                sleep(100);
                PspChargeResponse resp = new PspChargeResponse("failed", null, "insufficient_funds");
                cacheIfKeyed(request.idempotencyKey(), resp);
                yield ResponseEntity.ok(resp);
            }
            case "tok_card_declined" -> {
                sleep(100);
                PspChargeResponse resp = new PspChargeResponse("failed", null, "card_declined");
                cacheIfKeyed(request.idempotencyKey(), resp);
                yield ResponseEntity.ok(resp);
            }
            case "tok_timeout" -> {
                // Sleep 30 seconds then return success.
                // The calling service should timeout before this completes.
                sleep(30_000);
                PspChargeResponse resp = new PspChargeResponse("succeeded", UUID.randomUUID().toString(), null);
                cacheIfKeyed(request.idempotencyKey(), resp);
                yield ResponseEntity.ok(resp);
            }
            case "tok_network_error" -> {
                log.warn("PSP simulating network error (500)");
                yield ResponseEntity.internalServerError().body(Map.of("error", "internal_server_error"));
            }
            default -> {
                sleep(100);
                PspChargeResponse resp = new PspChargeResponse("succeeded", UUID.randomUUID().toString(), null);
                cacheIfKeyed(request.idempotencyKey(), resp);
                yield ResponseEntity.ok(resp);
            }
        };
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "ok", "service", "mock-psp"));
    }

    private void cacheIfKeyed(String key, PspChargeResponse response) {
        if (key != null) {
            idempotencyCache.put(key, response);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
