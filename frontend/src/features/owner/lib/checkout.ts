/*
 * 「이 학생에게 맡기기」 → 안전결제(/owner/works/:workId/pay).
 * 백엔드는 학생을 고를 때(select_applicant) 작업 id 를 만든다. 연동 전까지는
 * 「의뢰 id~학생 id」를 작업 id 자리에 써서 어느 의뢰의 어느 학생인지 알아본다.
 */
const SEPARATOR = "~";

export function checkoutWorkId(requestId: string, studentId: string): string {
  return `${requestId}${SEPARATOR}${studentId}`;
}

export function parseCheckoutWorkId(
  workId: string,
): { requestId: string; studentId: string } | undefined {
  const [requestId, studentId] = workId.split(SEPARATOR);
  return requestId && studentId ? { requestId, studentId } : undefined;
}
