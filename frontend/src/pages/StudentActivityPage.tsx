import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  EmpathyCount,
  RoleAvatar,
  SubScreen,
  SummaryCard,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  APPLICATION_STATUS_LABEL,
  ApplicationSheet,
  STUDENT_PATHS,
  SettlementSummaryBox,
  currentDeadline,
  deadlineText,
  useMyProposals,
  useStores,
  useStudentApplications,
  useStudentRequest,
  useStudentRequests,
  useStudentSettlements,
  useStudentWorks,
  workStatusText,
} from "../features/student";
import type {
  MyProposal,
  StudentActivityTab,
  StudentApplication,
  StudentWork,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import type { WorkKind } from "../types/workKind";
import "./StudentActivityPage.css";

const TABS: { tab: StudentActivityTab; label: string }[] = [
  { tab: "applied", label: "지원한 의뢰" },
  { tab: "proposals", label: "보낸 제안" },
  { tab: "inProgress", label: "진행 중" },
  { tab: "done", label: "완료" },
];

/** 카드 머리: 종류 아이콘 + 제목 + 오른쪽 (상태 칩 · 공감 수) */
function CardHead({ kind, title, right }: { kind: WorkKind; title: string; right?: ReactNode }) {
  return (
    <div className="student-activity__head">
      <WorkKindIcon kind={kind} />
      <h3 className="student-activity__title">{title}</h3>
      {right}
    </div>
  );
}

/** 가게 사진 + 이름 · 주소 */
function StoreLine({ name, address }: { name: string; address?: string }) {
  return (
    <div className="student-activity__store">
      <RoleAvatar role="owner" size={32} />
      <span className="student-activity__store-info">
        <strong>{name}</strong>
        {address && <span>{address}</span>}
      </span>
    </div>
  );
}

/**
 * 피그마 「내 활동 - 지원한 의뢰 · 보낸 제안 · 진행 중 · 완료 (학생)」.
 * 위 요약 카드 4칸이 탭이고, 고른 탭은 주소(?tab=)에 남아 돌아와도 그대로다.
 */
function StudentActivityPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.tab === params.get("tab"))?.tab ?? "applied";
  const applications = useStudentApplications();
  const proposals = useMyProposals();
  const works = useStudentWorks();
  const stores = useStores();
  const requests = useStudentRequests();
  const { summary } = useStudentSettlements();
  const [sheetRequestId, setSheetRequestId] = useState<string>();
  const sheetRequest = useStudentRequest(sheetRequestId);
  const sheetApplication = applications.find((a) => a.requestId === sheetRequestId);

  const addressOf = (storeId: string) => stores.find((s) => s.id === storeId)?.address;
  // 진행 중은 지금 지켜야 할 마감이 빠른 것부터
  const inProgress = works
    .filter((w) => ["drafting", "revising", "submitted"].includes(w.status))
    .sort((a, b) => currentDeadline(a).due.localeCompare(currentDeadline(b).due));
  const done = works
    .filter((w) => w.status === "completed")
    .sort((a, b) => (b.completedOn ?? "").localeCompare(a.completedOn ?? ""));
  const canceled = works.filter((w) => w.status === "canceled");
  const counts: Record<StudentActivityTab, number> = {
    applied: applications.length,
    proposals: proposals.length,
    inProgress: inProgress.length,
    done: done.length,
  };
  const selectedIndex = TABS.findIndex((t) => t.tab === tab);

  const appliedCard = (application: StudentApplication) => {
    const request = requests.find((r) => r.id === application.requestId);
    if (!request) return null;
    const reviewing = application.status === "reviewing";
    return (
      <li key={application.requestId} className="student-activity__card">
        <CardHead kind="request" title={request.title} />
        <div className="student-activity__meta">
          <CategoryBadge field={request.field} />
          <span>
            {request.store.name}, {APPLICATION_STATUS_LABEL[application.status]}
          </span>
          <TextButton
            className="student-activity__push"
            onClick={() =>
              reviewing
                ? navigate(STUDENT_PATHS.requestFull(request.id))
                : setSheetRequestId(request.id)
            }
          >
            {reviewing ? "의뢰서 보기" : "지원 결과 보기"}
          </TextButton>
        </div>
        <p className="student-activity__line">{deadlineText("draft", request.draftDue)}</p>
        {reviewing && (
          <>
            <div className="student-activity__divider" />
            <Button tone="student" size="medium" fullWidth onClick={() => setSheetRequestId(request.id)}>
              내 지원서 보기
            </Button>
          </>
        )}
      </li>
    );
  };

  const proposalCard = (proposal: MyProposal) => {
    const accepted = proposal.status === "accepted";
    return (
      <li key={proposal.id} className="student-activity__card">
        <CardHead
          kind="proposal"
          title={proposal.title}
          right={
            <>
              <span className="student-activity__chip">{accepted ? "수락됨" : "수락 대기 중"}</span>
              <EmpathyCount count={proposal.empathyCount} empathized />
            </>
          }
        />
        <div className="student-activity__meta">
          <CategoryBadge field={proposal.field} />
        </div>
        <div className="student-activity__box student-activity__box--column">
          <p className="student-activity__excerpt">{proposal.solution}</p>
          <TextButton onClick={() => navigate(STUDENT_PATHS.proposal(proposal.id))}>상세보기</TextButton>
        </div>
        <div className="student-activity__divider" />
        <StoreLine name={proposal.store.name} address={addressOf(proposal.store.id)} />
        {accepted && proposal.workId && (
          <>
            <div className="student-activity__divider" />
            <Button
              tone="student"
              size="medium"
              fullWidth
              onClick={() => navigate(STUDENT_PATHS.workStart(proposal.workId ?? ""))}
            >
              조건 확인하기
            </Button>
          </>
        )}
      </li>
    );
  };

  const inProgressCard = (work: StudentWork) => {
    const deadline = currentDeadline(work);
    const detailPath =
      work.status === "drafting"
        ? STUDENT_PATHS.workSubmit(work.id)
        : work.status === "revising"
          ? STUDENT_PATHS.workRevision(work.id)
          : STUDENT_PATHS.workSubmitted(work.id);
    return (
      <li key={work.id} className="student-activity__card">
        <CardHead kind={work.kind} title={work.title} />
        <div className="student-activity__meta">
          <CategoryBadge field={work.field} />
        </div>
        <p className="student-activity__line">{deadlineText(deadline.stage, deadline.due)}</p>
        <div className="student-activity__box">
          <span className="student-activity__dot" aria-hidden="true" />
          <span className="student-activity__status">{workStatusText(work)}</span>
          <TextButton onClick={() => navigate(detailPath)}>상세보기</TextButton>
        </div>
        <div className="student-activity__divider" />
        <StoreLine name={work.store.name} address={addressOf(work.store.id)} />
        <div className="student-activity__divider" />
        {work.status === "drafting" && (
          <Button tone="student" size="medium" fullWidth onClick={() => navigate(STUDENT_PATHS.workSubmit(work.id))}>
            초안 제출하기
          </Button>
        )}
        {work.status === "revising" && (
          <Button
            tone="student"
            size="medium"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.workRevisionSubmit(work.id))}
          >
            수정안 제출하기
          </Button>
        )}
        {work.status === "submitted" && (
          <Button tone="student" size="medium" fullWidth onClick={() => navigate(STUDENT_PATHS.chat(work.id))}>
            문의하기
          </Button>
        )}
      </li>
    );
  };

  const doneCard = (work: StudentWork) => {
    const auto = work.completedBy === "auto";
    return (
      <li key={work.id} className="student-activity__card">
        <CardHead
          kind={work.kind}
          title={work.title}
          right={<span className="student-activity__chip">{auto ? "자동 완료" : "완료"}</span>}
        />
        <div className="student-activity__meta">
          <CategoryBadge field={work.field} />
          <span>
            {work.store.name}, {formatMonthDay(work.completedOn ?? "")} {auto ? "자동 완료" : "완료"}
          </span>
        </div>
        <p className="student-activity__line">작업비 {formatWon(work.budget)} 정산 완료</p>
        <div className="student-activity__divider" />
        <div className="student-activity__footer">
          <TextButton onClick={() => navigate(STUDENT_PATHS.workResult(work.id))}>내 결과물 보기</TextButton>
          {work.review && (
            <TextButton onClick={() => navigate(STUDENT_PATHS.workReview(work.id))}>
              받은 후기 ★ {work.review.rating.toFixed(1)}
            </TextButton>
          )}
        </div>
      </li>
    );
  };

  const canceledCard = (work: StudentWork) => (
    <li key={work.id} className="student-activity__card">
      <CardHead
        kind={work.kind}
        title={work.title}
        right={<span className="student-activity__chip">취소됨</span>}
      />
      <div className="student-activity__meta">
        <CategoryBadge field={work.field} />
        <span>
          {work.store.name}, {work.cancel ? formatMonthDay(work.cancel.canceledOn) : ""} 사장님이 취소
        </span>
      </div>
      {work.cancel && work.cancel.reward > 0 && (
        <p className="student-activity__line">착수 보상 {formatWon(work.cancel.reward)} 정산 완료</p>
      )}
      <div className="student-activity__divider" />
      <div className="student-activity__footer">
        <TextButton onClick={() => navigate(STUDENT_PATHS.workCanceled(work.id))}>취소 상세보기</TextButton>
      </div>
    </li>
  );

  const listTitle = (label: string, count: number) => (
    <h2 className="student-activity__list-title">
      {label} <span>{count}</span>
    </h2>
  );

  return (
    <SubScreen title="내 활동" onBack={back}>
      <div className="student-activity">
        <SummaryCard
          items={TABS.map(({ tab: t, label }) => ({ label, count: counts[t] }))}
          selectedIndex={selectedIndex}
          onSelect={(i) => setParams({ tab: TABS[i].tab }, { replace: true })}
        />

        {tab === "done" && (
          <section className="student-activity__settlements">
            <div className="student-activity__settlements-head">
              <h2 className="student-activity__list-title">정산 내역</h2>
              <TextButton onClick={() => navigate(STUDENT_PATHS.settlements)}>전체 보기</TextButton>
            </div>
            <SettlementSummaryBox summary={summary} />
          </section>
        )}

        {listTitle(TABS[selectedIndex].label, counts[tab])}
        <ul className="student-activity__list">
          {tab === "applied" && applications.map(appliedCard)}
          {tab === "proposals" && proposals.map(proposalCard)}
          {tab === "inProgress" && inProgress.map(inProgressCard)}
          {tab === "done" && done.map(doneCard)}
        </ul>
        {counts[tab] === 0 && <p className="student-activity__empty">아직 없어요</p>}

        {tab === "done" && canceled.length > 0 && (
          <>
            {listTitle("취소된 일", canceled.length)}
            <ul className="student-activity__list">{canceled.map(canceledCard)}</ul>
          </>
        )}
      </div>

      <ApplicationSheet
        application={sheetApplication}
        request={sheetRequest}
        onClose={() => setSheetRequestId(undefined)}
      />
    </SubScreen>
  );
}

export default StudentActivityPage;
