#!/usr/bin/env node
/**
 * 알림을 누르면 가는 화면 확인 (사장님 · 학생, 백엔드 알림 종류 21종 모두).
 * 설치된 Vite 로 src 의 TypeScript 모듈을 그대로 불러와 node:assert 로 비교한다 (테스트 도구를 따로 두지 않는다).
 * 대상 종류 · id 는 백엔드 NotificationEventFactory 가 만드는 것과 같다.
 */
import assert from "node:assert/strict";
import { createServer } from "vite";

const JOB = "12";
const PROPOSAL = "34";
const ROOM = "01JCHATROOMULID0000000000";
const PAYMENT = "56";

/** 종류 → 백엔드가 보내는 대상 종류 · id */
const TARGETS = {
  JOB_DRAFT_SUBMITTED: ["JOB", JOB],
  PROPOSAL_RECEIVED: ["PROPOSAL", PROPOSAL],
  JOB_APPLICATION_RECEIVED: ["JOB", JOB],
  CHAT_MESSAGE_RECEIVED: ["CHAT_ROOM", ROOM],
  PAYMENT_COMPLETED: ["PAYMENT", PAYMENT],
  JOB_COMPLETED: ["JOB", JOB],
  PROPOSAL_LIKE_MILESTONE_REACHED: ["PROPOSAL", PROPOSAL],
  JOB_APPLICATION_SELECTED: ["CHAT_ROOM", ROOM],
  JOB_APPLICATION_REJECTED: ["JOB", JOB],
  PROPOSAL_REJECTED: ["PROPOSAL", PROPOSAL],
  PROPOSAL_CANCELLED: ["PROPOSAL", PROPOSAL],
  PROPOSAL_ACCEPTED: ["JOB", JOB],
  JOB_STARTED: ["CHAT_ROOM", ROOM],
  JOB_REVISION_REQUESTED: ["JOB", JOB],
  JOB_REVISION_SUBMITTED: ["JOB", JOB],
  JOB_REVIEW_REQUESTED: ["JOB", JOB],
  JOB_REVIEW_RECEIVED: ["JOB", JOB],
  JOB_RECRUITMENT_CANCELLED: ["JOB", JOB],
  JOB_CANCELLED_BY_OWNER: ["CHAT_ROOM", ROOM],
  PAYMENT_REFUNDED: ["PAYMENT", PAYMENT],
  PAYMENT_SETTLED: ["JOB", JOB],
};

const OWNER = {
  JOB_DRAFT_SUBMITTED: `/owner/works/${JOB}/check`,
  PROPOSAL_RECEIVED: `/owner/proposals/${PROPOSAL}`,
  JOB_APPLICATION_RECEIVED: `/owner/requests/${JOB}/applicants`,
  CHAT_MESSAGE_RECEIVED: `/owner/chats/${ROOM}`,
  PAYMENT_COMPLETED: "/owner/me/payments",
  JOB_COMPLETED: `/owner/works/${JOB}/result`,
  PROPOSAL_LIKE_MILESTONE_REACHED: `/owner/proposals/${PROPOSAL}`,
  JOB_APPLICATION_SELECTED: `/owner/chats/${ROOM}`,
  JOB_APPLICATION_REJECTED: `/owner/requests/${JOB}`,
  PROPOSAL_REJECTED: `/owner/proposals/${PROPOSAL}`,
  // 서버가 취소된 제안을 사장님에게 주지 않아 내 활동 › 받은 제안으로
  PROPOSAL_CANCELLED: "/owner/requests?tab=proposals",
  PROPOSAL_ACCEPTED: `/owner/requests/${JOB}`,
  JOB_STARTED: `/owner/chats/${ROOM}`,
  JOB_REVISION_REQUESTED: `/owner/requests/${JOB}`,
  JOB_REVISION_SUBMITTED: `/owner/works/${JOB}/check`,
  JOB_REVIEW_REQUESTED: `/owner/works/${JOB}/review`,
  JOB_REVIEW_RECEIVED: `/owner/requests/${JOB}`,
  JOB_RECRUITMENT_CANCELLED: `/owner/requests/${JOB}`,
  JOB_CANCELLED_BY_OWNER: `/owner/chats/${ROOM}`,
  PAYMENT_REFUNDED: "/owner/me/payments",
  PAYMENT_SETTLED: `/owner/requests/${JOB}`,
};

const STUDENT = {
  JOB_DRAFT_SUBMITTED: `/student/works/${JOB}/submit`,
  PROPOSAL_RECEIVED: `/student/proposals/${PROPOSAL}`,
  JOB_APPLICATION_RECEIVED: `/student/works/${JOB}/submit`,
  CHAT_MESSAGE_RECEIVED: `/student/chats/${ROOM}`,
  PAYMENT_COMPLETED: "/student/me/settlements",
  JOB_COMPLETED: `/student/works/${JOB}/result`,
  PROPOSAL_LIKE_MILESTONE_REACHED: `/student/proposals/${PROPOSAL}`,
  JOB_APPLICATION_SELECTED: `/student/chats/${ROOM}`,
  JOB_APPLICATION_REJECTED: `/student/requests/${JOB}/full`,
  PROPOSAL_REJECTED: `/student/proposals/${PROPOSAL}`,
  PROPOSAL_CANCELLED: `/student/proposals/${PROPOSAL}`,
  PROPOSAL_ACCEPTED: `/student/proposals/${PROPOSAL}/start`,
  JOB_STARTED: `/student/chats/${ROOM}`,
  JOB_REVISION_REQUESTED: `/student/works/${JOB}/revision`,
  JOB_REVISION_SUBMITTED: `/student/works/${JOB}/submit`,
  JOB_REVIEW_REQUESTED: `/student/works/${JOB}/submit`,
  JOB_REVIEW_RECEIVED: `/student/works/${JOB}/review`,
  JOB_RECRUITMENT_CANCELLED: `/student/requests/${JOB}/full`,
  JOB_CANCELLED_BY_OWNER: `/student/chats/${ROOM}`,
  PAYMENT_REFUNDED: "/student/me/settlements",
  PAYMENT_SETTLED: "/student/me/settlements",
};

const item = (type, targetType, targetId) => ({
  id: 1,
  type,
  title: "",
  body: "",
  targetType,
  targetId,
  read: false,
  createdAt: "2026-10-08T00:00:00.000Z",
});

const server = await createServer({
  server: { middlewareMode: true, hmr: false },
  appType: "custom",
  logLevel: "error",
});

let checked = 0;
try {
  const notification = await server.ssrLoadModule("/src/features/notification/lib/notifications.ts");
  const owner = await server.ssrLoadModule("/src/features/owner/lib/notifications.ts");
  const student = await server.ssrLoadModule("/src/features/student/lib/notifications.ts");
  const proposals = async () => [
    { proposalId: 99, jobId: null },
    { proposalId: Number(PROPOSAL), jobId: Number(JOB) },
  ];

  assert.equal(Object.keys(TARGETS).length, 21, "백엔드 알림 종류는 21종");
  for (const [type, [targetType, targetId]] of Object.entries(TARGETS)) {
    assert.ok(notification.isKnownNotificationType(type), `${type} 는 아는 종류`);
    assert.notEqual(notification.notificationIcon(type), "🔔", `${type} 아이콘`);
    const n = item(type, targetType, targetId);
    assert.equal(owner.notificationPath(n), OWNER[type], `사장님 ${type}`);
    assert.equal(await student.resolveNotificationPath(n, proposals), STUDENT[type], `학생 ${type}`);
    checked += 2;
  }

  // 수락된 제안: 보낸 제안에 그 의뢰가 없거나 불러오지 못하면 내 활동 › 보낸 제안
  const accepted = item("PROPOSAL_ACCEPTED", "JOB", JOB);
  assert.equal(student.notificationPath(accepted), undefined, "수락된 제안은 바로 정하지 않는다");
  assert.equal(await student.resolveNotificationPath(accepted, async () => []), "/student/activity?tab=proposals");
  assert.equal(
    await student.resolveNotificationPath(accepted, async () => {
      throw new Error("offline");
    }),
    "/student/activity?tab=proposals",
  );
  checked += 3;

  // 모르는 종류는 대상 종류의 기본 화면으로 간다 (아이콘은 🔔)
  const unknownDefaults = [
    ["JOB", JOB, `/owner/requests/${JOB}`, `/student/works/${JOB}/submit`],
    ["PROPOSAL", PROPOSAL, `/owner/proposals/${PROPOSAL}`, `/student/proposals/${PROPOSAL}`],
    ["CHAT_ROOM", ROOM, `/owner/chats/${ROOM}`, `/student/chats/${ROOM}`],
    ["PAYMENT", PAYMENT, "/owner/me/payments", "/student/me/settlements"],
  ];
  for (const [targetType, targetId, ownerPath, studentPath] of unknownDefaults) {
    const n = item("SOMETHING_NEW", targetType, targetId);
    assert.equal(owner.notificationPath(n), ownerPath, `사장님 모르는 종류 → ${targetType} 기본`);
    assert.equal(await student.resolveNotificationPath(n, proposals), studentPath, `학생 모르는 종류 → ${targetType} 기본`);
    checked += 2;
  }

  // 모르는 대상, 숫자가 아닌 id 는 이동하지 않는다
  for (const n of [
    item("SOMETHING_NEW", "SOMETHING", JOB),
    item("JOB_DRAFT_SUBMITTED", "SOMETHING", JOB),
    item("JOB_DRAFT_SUBMITTED", "JOB", "abc"),
    item("PROPOSAL_RECEIVED", "PROPOSAL", "012"),
    item("JOB_STARTED", "CHAT_ROOM", ""),
  ]) {
    assert.equal(owner.notificationPath(n), undefined, `사장님 ${n.type}/${n.targetType}/${n.targetId}`);
    assert.equal(await student.resolveNotificationPath(n, proposals), undefined, `학생 ${n.type}/${n.targetType}/${n.targetId}`);
    checked += 2;
  }
  assert.equal(notification.notificationIcon("SOMETHING_NEW"), "🔔");
  checked += 1;
} finally {
  await server.close();
}

console.log(`notification paths: ${checked} checks passed`);
