package io.bedrockbridge.application.javawire;

/** Safe, bounded reason received from a Java disconnect packet. */
public final class JavaUpstreamDisconnect extends JavaWireException {
  private static final long serialVersionUID = 1L;

  private final String reasonJson;

  public JavaUpstreamDisconnect(String reasonJson) {
    super("Java upstream disconnected: " + sanitize(reasonJson));
    this.reasonJson = sanitize(reasonJson);
  }

  public String reasonJson() {
    return reasonJson;
  }

  private static String sanitize(String value) {
    if (value == null || value.isBlank()) {
      return "{\"text\":\"Java upstream disconnected\"}";
    }
    return value.length() > 4096 ? value.substring(0, 4096) : value;
  }
}
