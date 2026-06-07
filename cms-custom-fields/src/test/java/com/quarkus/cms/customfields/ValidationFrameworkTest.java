package com.quarkus.cms.customfields;

import static org.junit.jupiter.api.Assertions.*;

import com.quarkus.cms.customfields.validation.ValidationConfig;
import com.quarkus.cms.customfields.validation.ValidationResult;

import java.util.Map;

import org.junit.jupiter.api.Test;

/** Tests for the validation framework. */
class ValidationFrameworkTest {

  @Test
  void shouldPassForNullNonRequiredValue() {
    ValidationResult result = new ValidationResult();
    assertTrue(result.isValid());
  }

  @Test
  void shouldReportSingleError() {
    ValidationResult r = ValidationResult.error("Field 'name' is required");
    assertFalse(r.isValid());
    assertEquals("Field 'name' is required", r.toErrorMessage());
  }

  @Test
  void shouldReportMultipleErrors() {
    ValidationResult r = new ValidationResult();
    r.addError("Error 1");
    r.addError("Error 2");
    assertFalse(r.isValid());
    assertEquals(2, r.getErrors().size());
    assertTrue(r.toErrorMessage().contains("Error 1"));
  }

  @Test
  void shouldMergeResults() {
    ValidationResult r1 = ValidationResult.error("First error");
    ValidationResult r2 = ValidationResult.valid();
    r2.addError("Second error");
    r1.addAll(r2);
    assertEquals(2, r1.getErrors().size());
  }

  @Test
  void shouldThrowOnInvalid() {
    ValidationResult r = ValidationResult.error("Field X is invalid");
    assertThrows(IllegalArgumentException.class, r::throwIfInvalid);
  }

  @Test
  void shouldNotThrowOnValid() {
    assertDoesNotThrow(() -> ValidationResult.valid().throwIfInvalid());
  }

  @Test
  void shouldConfigFromOptions() {
    Map<String, Object> options =
        Map.of(
            "required",
            true,
            "unique",
            true,
            "minLength",
            2,
            "maxLength",
            100,
            "regex",
            "^[a-z]+$");
    ValidationConfig config = ValidationConfig.fromOptions(options);
    assertTrue(config.isRequired());
    assertTrue(config.isUnique());
    assertEquals(2, config.getMinLength());
    assertEquals(100, config.getMaxLength());
    assertEquals("^[a-z]+$", config.getRegex());
  }

  @Test
  void shouldHandleNullOptions() {
    ValidationConfig config = ValidationConfig.fromOptions(null);
    assertFalse(config.isRequired());
    assertFalse(config.isUnique());
    assertNull(config.getMinLength());
  }

  @Test
  void shouldConvertConfigToMap() {
    ValidationConfig config = new ValidationConfig(true, false, 1, 10, null, null, "^[a-z]+$");
    Map<String, Object> map = config.toMap();
    assertTrue((Boolean) map.get("required"));
    assertEquals(1, map.get("minLength"));
    assertEquals(10, map.get("maxLength"));
    assertEquals("^[a-z]+$", map.get("regex"));
    assertFalse(map.containsKey("min"));
  }
}
