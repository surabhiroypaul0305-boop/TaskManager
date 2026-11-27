package TaskManager.parser;

import java.io.IOException;

/**
 * INSTRUCTOR-PROVIDED (Steps 4, 5, 9).
 *
 * The ONLY thing the rest of the program knows about "talking to a model":
 * send a prompt String, get the reply String back. It is an interface so tests can plug
 * in a FakeLlmClient / FaultyLlmClient (Strategy pattern + Dependency Inversion).
 *
 * Contract:
 *  - Returns the model's reply text.
 *  - Throws IOException if the call fails or times out (catch it -> BadGatewayException).
 *
 * The real implementation is HttpLlmClient. Students never edit it.
 *
 * When you copy this into your own project, change the package line to: package parser;
 */
public interface LlmClient {

    String send(String prompt) throws IOException;
}
