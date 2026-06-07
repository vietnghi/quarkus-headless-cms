package com.quarkus.cms.webhooks.service;

import jakarta.enterprise.context.ApplicationScoped;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Generates and validates HMAC-SHA256 signatures for webhook payloads.
 *
 * <p>When a webhook is configured with a secret key, every outgoing POST includes an {@code
 * X-Cms-Signature} header containing the Base64-encoded HMAC-SHA256 of the payload body.
 * Subscribers can verify the signature to ensure payload integrity and that the request originates
 * from the CMS.
 *
 * <p>Algorithm: HMAC-SHA256 over the raw JSON payload bytes, Base64-encoded. Header: {@code
 * X-Cms-Signature: <signature>}
 */
@ApplicationScoped
public class WebhookSecurityService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  public static final String SIGNATURE_HEADER = "X-Cms-Signature";

  /**
   * Generates an HMAC-SHA256 signature for the given payload body.
   *
   * @param secret the webhook secret key
   * @param payload the raw payload body (JSON string)
   * @return Base64-encoded HMAC-SHA256 signature
   */
  public String sign(String secret, String payload) {
    if (secret == null || secret.isBlank()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      SecretKeySpec keySpec =
          new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
      mac.init(keySpec);
      byte[] hmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hmac);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("HMAC-SHA256 algorithm not available", e);
    } catch (InvalidKeyException e) {
      throw new IllegalArgumentException("Invalid HMAC secret key", e);
    }
  }

  /**
   * Verifies an incoming HMAC signature against the payload.
   *
   * @param secret the expected secret
   * @param payload the raw payload body
   * @param signature the received Base64-encoded signature
   * @return true if the signature is valid
   */
  public boolean verify(String secret, String payload, String signature) {
    if (secret == null || secret.isBlank() || signature == null || signature.isBlank()) {
      return false;
    }
    String expected = sign(secret, payload);
    if (expected == null) {
      return false;
    }
    // Constant-time comparison to prevent timing attacks
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
  }

  // Internal constant-time byte array comparison (no external dependency needed)
  private static final class MessageDigest {
    static boolean isEqual(byte[] a, byte[] b) {
      if (a.length != b.length) {
        return false;
      }
      int result = 0;
      for (int i = 0; i < a.length; i++) {
        result |= a[i] ^ b[i];
      }
      return result == 0;
    }
  }
}
