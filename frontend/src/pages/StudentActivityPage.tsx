import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  EmpathyCount,
  LoadNotice,
  RoleAvatar,
  SubScreen,
  SummaryCard,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  ApplicationSheet,
  STUDENT_PATHS,
  SettlementSummaryBox,
  currentDeadline,
  deadlineText,
  sentOnText,
  sentProposalStatusLabel,
  appliedStatusLabel,
  storeAddressText,
  useAppliedJobs,
  useSentProposals,
  useStores,
  useStudentSettlements,
  useStudentWorks,
  workStatusText,
} from "../features/student";
import { proposalBadgeNames } from "../features/proposal";
import type {
  AppliedJob,
  SentProposal,
  StudentActivityTab,
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
 * 지원한 의뢰는 GET /me/job-applications (ADR 0027), 보낸 제안은 GET /me/proposals (ADR 0023).
 * 진행 중 · 완료 탭은 아직 샘플 데이터다.
 */
function StudentActivityPage() {
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.me);
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.tab === params.get("tab"))?.tab ?? "applied";
  const { load: appliedLoad, reload: reloadApplied } = useAppliedJobs();
  const applied = appliedLoad.status === "loaded" ? appliedLoad.jobs : [];
  const { load: proposalsLoad, reload: reloadProposals } = useSentProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const works = useStudentWorks();
  const stores = useStores();
  const { summary } = useStudentSettlements();
  const [sheetJobId, setSheetJobId] = useState<number>();
  const sheetJob = applied.find((job) => job.jobId === sheetJobId);

  const addressOf = (storeId: string) => stores.find((s) => s.id === storeId)?.address;
  // 진행 중은 지금 지켜야 할 마감이 빠른 것부터
  const inProgress = works
    .filter((w) => ["drafting", "revising", "submitted"].includes(w.status))
    .sort((a, b) => currentDeadline(a).due.localeCompare(currentDeadline(b).due));
  const done = works
    .filter((w) => w.status === "completed")
    .sort((a, b) => (b.completedOn ?? "").localeCompare(a.completedOn ?? ""));
  const canceled = works.filter((w) => w.status === "canceled");
  // 지원한 의뢰 · 보낸 제안을 불러오는 중이거나 실패하면 개수 대신 「-」
  const counts: Record<StudentActivityTab, number | string> = {
    applied: appliedLoad.status === "loaded" ? applied.length : "-",
    proposals: proposalsLoad.status === "loaded" ? proposals.length : "-",
    inProgress: inProgress.length,
    done: done.length,
  };
  const selectedIndex = TABS.findIndex((t) => t.tab === tab);

  const appliedCard = (job: AppliedJob) => {
    const reviewing = job.applicationStatus === "PENDING";
    return (
      <li key={job.jobApplicationId} className="student-activity__card">
        <CardHead kind="request" title={job.title} />
        <div className="student-activity__meta">
          {proposalBadgeNames(job.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          <span>
            {[job.storeName, appliedStatusLabel(job.applicationStatus)].filter(Boolean).join(", ")}
          </span>
          <TextButton
            className="student-activity__push"
            onClick={() =>
              reviewing
                ? navigate(STUDENT_PATHS.requestFull(String(job.jobId)))
                : setSheetJobId(job.jobId)
            }
          >
            {reviewing ? "의뢰서 보기" : "지원 결과 보기"}
          </TextButton>
        </div>
        <p className="student-activity__line">{deadlineText("draft", job.draftDeadline)}</p>
        {reviewing && (
          <>
            <div className="student-activity__divider" />
            <Button tone="student" size="medium" fullWidth onClick={() => setSheetJobId(job.jobId)}>
              내 지원서 보기
            </Button>
          </>
        )}
      </li>
    );
  };

  const proposalCard = (proposal: SentProposal) => {
    const openDetail = () => navigate(STUDENT_PATHS.proposal(String(proposal.proposalId)));
    const sentOn = sentOnText(proposal.createdAt);
    return (
      <li key={proposal.proposalId} className="student-activity__card">
        <CardHead
          kind="proposal"
          title={proposal.title}
          right={
            <>
              <span className="student-activity__chip">{sentProposalStatusLabel(proposal.status, proposal.jobStatus)}</span>
              <EmpathyCount count={proposal.likeCount} empathized />
            </>
          }
        />
        <div className="student-activity__meta student-activity__meta--wrap">
          {proposalBadgeNames(proposal.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          {sentOn && <span>{sentOn}</span>}
        </div>
        <div className="student-activity__box student-activity__box--column">
          <p className="student-activity__excerpt">{proposal.proposedSolution}</p>
          <TextButton onClick={openDetail}>상세보기</TextButton>
        </div>
        <div className="student-activity__divider" />
        <StoreLine name={proposal.store.storeName} address={storeAddressText(proposal.store.storeAddress)} />
        {proposal.status === "AWAITING_START" && proposal.jobStatus !== "CANCELLED" && (
          <>
            <div className="student-activity__divider" />
            <Button
              tone="student"
              size="medium"
              fullWidth
              onClick={() => navigate(STUDENT_PATHS.proposalStart(String(proposal.proposalId)))}
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

  const listTitle = (label: string, count: number | string) => (
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
          {tab === "applied" && applied.map(appliedCard)}
          {tab === "proposals" && proposals.map(proposalCard)}
          {tab === "inProgress" && inProgress.map(inProgressCard)}
          {tab === "done" && done.map(doneCard)}
        </ul>
        {tab === "applied" && appliedLoad.status !== "loaded" && (
          <LoadNotice
            status={appliedLoad.status}
            loadingText="지원한 의뢰를 불러오는 중이에요"
            errorText="지원한 의뢰를 불러오지 못했어요"
            onRetry={reloadApplied}
          />
        )}
        {tab === "proposals" && proposalsLoad.status !== "loaded" && (
          <LoadNotice
            status={proposalsLoad.status}
            loadingText="보낸 제안을 불러오는 중이에요"
            errorText="보낸 제안을 불러오지 못했어요"
            onRetry={reloadProposals}
          />
        )}
        {counts[tab] === 0 && <p className="student-activity__empty">아직 없어요</p>}

        {tab === "done" && canceled.length > 0 && (
          <>
            {listTitle("취소된 일", canceled.length)}
            <ul className="student-activity__list">{canceled.map(canceledCard)}</ul>
          </>
        )}
      </div>

      <ApplicationSheet job={sheetJob} onClose={() => setSheetJobId(undefined)} />
    </SubScreen>
  );
}

export default StudentActivityPage;
