package org.whispersystems.waveservice.api.websocket;

import org.whispersystems.waveservice.internal.websocket.WebSocketConnection;

public interface WebSocketFactory {
  WebSocketConnection createConnection() throws WebSocketUnavailableException;
}
