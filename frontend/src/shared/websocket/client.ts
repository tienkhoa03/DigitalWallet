/**
 * Single shared WebSocket connection per session (.claude/rules/frontend_coding.md §13).
 * MUST NOT open multiple raw `new WebSocket(...)` instances — per-feature channels are
 * multiplexed through this client.
 *
 * Skeleton: subscribe/unsubscribe hook contract only. Full multiplex (fraud alerts /
 * threshold notifications / advisor reply) wires per feature module.
 */
type Listener = (payload: unknown) => void;

export interface WebSocketClient {
  subscribe(channel: string, listener: Listener): () => void;
  publish(channel: string, payload: unknown): void;
}

let socket: WebSocket | null = null;
const listeners: Map<string, Set<Listener>> = new Map();

function ensureConnection(url: string): WebSocket {
  if (socket && socket.readyState === WebSocket.OPEN) return socket;
  socket = new WebSocket(url);
  socket.addEventListener('message', (event) => {
    try {
      const envelope = JSON.parse(event.data) as { channel: string; payload: unknown };
      const bucket = listeners.get(envelope.channel);
      bucket?.forEach((l) => { l(envelope.payload); });
    } catch (e) {
      console.error('ws message parse failed', e);
    }
  });
  return socket;
}

export function createWebSocketClient(url: string): WebSocketClient {
  return {
    subscribe(channel, listener) {
      ensureConnection(url);
      let bucket = listeners.get(channel);
      if (!bucket) {
        bucket = new Set();
        listeners.set(channel, bucket);
      }
      bucket.add(listener);
      return () => {
        bucket?.delete(listener);
      };
    },
    publish(channel, payload) {
      const ws = ensureConnection(url);
      const send = () => { ws.send(JSON.stringify({ channel, payload })); };
      if (ws.readyState === WebSocket.OPEN) send();
      else ws.addEventListener('open', send, { once: true });
    },
  };
}
