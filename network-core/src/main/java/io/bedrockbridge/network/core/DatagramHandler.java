package io.bedrockbridge.network.core;

/** Consumes a validated UDP datagram on the transport I/O thread. */
@FunctionalInterface
public interface DatagramHandler {
    /** Handles one datagram; implementations must not retain its payload after returning. */
    void handle(Datagram datagram);
}
