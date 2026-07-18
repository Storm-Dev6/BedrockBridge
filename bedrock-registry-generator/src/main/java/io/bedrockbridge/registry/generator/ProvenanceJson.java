package io.bedrockbridge.registry.generator;

public final class ProvenanceJson {
  private ProvenanceJson() {}

  public static String write(BdsProvenanceManifest manifest) {
    StringBuilder json = new StringBuilder(1024);
    json.append("{\n");
    field(json, "schema", BdsProvenanceManifest.SCHEMA, true);
    field(json, "disclaimer", BdsProvenanceManifest.DISCLAIMER, true);
    field(json, "product", "Minecraft Bedrock Dedicated Server", true);
    field(json, "version", manifest.source().version(), true);
    field(json, "sourceUrl", manifest.source().downloadUri().toASCIIString(), true);
    field(json, "inspectedAt", manifest.inspectedAt().toString(), true);
    field(json, "distributionSha256", manifest.distributionSha256(), true);
    json.append("  \"distributionSize\": ").append(manifest.distributionSize()).append(",\n");
    json.append("  \"files\": [\n");
    for (int index = 0; index < manifest.files().size(); index++) {
      FileDigest file = manifest.files().get(index);
      json.append("    {\"path\": \"")
          .append(escape(file.path()))
          .append("\", \"size\": ")
          .append(file.size())
          .append(", \"sha256\": \"")
          .append(file.sha256())
          .append("\"}");
      json.append(index + 1 == manifest.files().size() ? '\n' : ",\n");
    }
    json.append("  ]\n}\n");
    return json.toString();
  }

  private static void field(StringBuilder output, String name, String value, boolean comma) {
    output.append("  \"").append(name).append("\": \"").append(escape(value)).append('"');
    if (comma) {
      output.append(',');
    }
    output.append('\n');
  }

  private static String escape(String value) {
    StringBuilder escaped = new StringBuilder(value.length());
    for (int index = 0; index < value.length(); index++) {
      char character = value.charAt(index);
      switch (character) {
        case '"' -> escaped.append("\\\"");
        case '\\' -> escaped.append("\\\\");
        case '\b' -> escaped.append("\\b");
        case '\f' -> escaped.append("\\f");
        case '\n' -> escaped.append("\\n");
        case '\r' -> escaped.append("\\r");
        case '\t' -> escaped.append("\\t");
        default -> {
          if (character < 0x20) {
            escaped.append(String.format("\\u%04x", (int) character));
          } else {
            escaped.append(character);
          }
        }
      }
    }
    return escaped.toString();
  }
}
