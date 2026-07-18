package io.bedrockbridge.application.translation;

import io.bedrockbridge.application.javawire.JavaTcpConnection;
import io.bedrockbridge.application.javawire.JavaWirePacket;
import io.bedrockbridge.bedrock.packet.play.BedrockPlayPacket;
import java.io.IOException;
import java.util.List;

/** One bridge session's Java socket and the first downstream packets it unlocks. */
public final class JavaUpstreamConnection implements AutoCloseable {
  private final JavaTcpConnection transport;
  private final JavaBedrockTranslator translator;
  private final JavaWirePacket.LoginSuccess loginSuccess;

  private JavaUpstreamConnection(
      JavaTcpConnection transport,
      JavaBedrockTranslator translator,
      JavaWirePacket.LoginSuccess loginSuccess) {
    this.transport = transport;
    this.translator = translator;
    this.loginSuccess = loginSuccess;
  }

  public static JavaUpstreamConnection loginOffline(
      String host,
      int port,
      String username,
      int connectTimeoutMillis,
      int readTimeoutMillis,
      JavaBedrockTranslator translator)
      throws IOException, io.bedrockbridge.application.javawire.JavaWireException {
    JavaTcpConnection connection =
        JavaTcpConnection.connect(
            host, port, connectTimeoutMillis, readTimeoutMillis, ignored -> {});
    try {
      JavaWirePacket.LoginSuccess success = connection.loginOffline(host, port, username);
      return new JavaUpstreamConnection(connection, translator, success);
    } catch (IOException | io.bedrockbridge.application.javawire.JavaWireException failure) {
      connection.close();
      throw failure;
    }
  }

  public JavaWirePacket.LoginSuccess loginSuccess() {
    return loginSuccess;
  }

  public List<BedrockPlayPacket> loginPackets() {
    return translator.onJavaLoginSuccess(loginSuccess);
  }

  public List<BedrockPlayPacket> resourcePackFlowStart() {
    return translator.onResourcePackFlowStart();
  }

  public long keepAlive(long payload) {
    return translator.onJavaKeepAlive(payload);
  }

  @Override
  public void close() throws IOException {
    transport.close();
  }
}
