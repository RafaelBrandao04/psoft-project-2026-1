package pt.psoft.g1.psoftg1.shared.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StringUtilsCustomTest {

    @Test
    void testIsAlphanumeric_ValidInputs() {
        assertThat(StringUtilsCustom.isAlphanumeric("Clean Text 123")).isTrue();
        assertThat(StringUtilsCustom.isAlphanumeric("João-Maria")).isTrue();
        assertThat(StringUtilsCustom.isAlphanumeric("O'Connor")).isTrue();
        assertThat(StringUtilsCustom.isAlphanumeric("")).isTrue();
    }

    @Test
    void testIsAlphanumeric_InvalidInputs() {
        assertThat(StringUtilsCustom.isAlphanumeric("Text with @symbol")).isFalse();
        assertThat(StringUtilsCustom.isAlphanumeric("Dangerous <script>")).isFalse();
        assertThat(StringUtilsCustom.isAlphanumeric("Value#1")).isFalse();
        assertThat(StringUtilsCustom.isAlphanumeric("Price: 10$")).isFalse();
    }

    @Test
    void testStartsOrEndsInWhiteSpace() {
        assertThat(StringUtilsCustom.startsOrEndsInWhiteSpace("valid text")).isTrue();
        assertThat(StringUtilsCustom.startsOrEndsInWhiteSpace(" leading")).isFalse();
        assertThat(StringUtilsCustom.startsOrEndsInWhiteSpace("trailing ")).isFalse();
        assertThat(StringUtilsCustom.startsOrEndsInWhiteSpace(" both ")).isFalse();
    }

    @Test
    void testSanitizeHtml_StripsMaliciousScripts() {
        String unsafe = "<script>alert('xss')</script>Safe Content";
        String sanitized = StringUtilsCustom.sanitizeHtml(unsafe);

        assertThat(sanitized).doesNotContain("<script>");
        assertThat(sanitized).contains("Safe Content");
    }

    @Test
    void testSanitizeHtml_AllowsSafeFormatting() {
        String safe = "<b>Bold</b> and <i>Italic</i>";
        String sanitized = StringUtilsCustom.sanitizeHtml(safe);

        assertThat(sanitized).isEqualTo("<b>Bold</b> and <i>Italic</i>");
    }
}
