/*
 * 읽음 처리(PUT /read)와 안 읽은 수를 맞춘다. 채팅 목록 · 탭 점은 보내는 중인 읽음 처리가
 * 끝난 뒤에 불러오고, 읽음 처리가 끝날 때마다 다시 불러온다.
 */

const pending = new Set<Promise<unknown>>();
const listeners = new Set<() => void>();

/** 읽음 처리 요청을 맡긴다. 성공하든 실패하든 끝나면 구독자에게 알린다 */
export function trackRead(request: Promise<unknown>): void {
  pending.add(request);
  const done = () => {
    pending.delete(request);
    listeners.forEach((listener) => listener());
  };
  request.then(done, done);
}

/** 지금 보내는 중인 읽음 처리가 모두 끝나면 풀린다 */
export function readsSettled(): Promise<void> {
  return Promise.allSettled([...pending]).then(() => undefined);
}

/** 읽음 처리가 끝날 때마다 부른다. 돌려준 함수로 그만 듣는다 */
export function onReadSettled(listener: () => void): () => void {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}
