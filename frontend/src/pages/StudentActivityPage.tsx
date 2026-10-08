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
  settlementSummaryOf,
  deadlineText,
  progressDeadline,
  progressStatusText,
  sentOnText,
  sentProposalInProgress,
  sentProposalStatusLabel,
  appliedStatusLabel,
  storeAddressText,
  useAppliedJobs,
  useProgressJobs,
  useSentProposals,
  useFinishedJobs,
  useSettlementHistory,
} from "../features/student";
import { useOpenJobChat } from "../features/chat";
import { proposalBadgeNames } from "../features/proposal";
import type {
  AppliedJob,
  FinishedJob,
  ProgressJob,
  SentProposal,
  StudentActivityTab,
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
function StoreLine({ name, address, photo }: { name: string; address?: string; photo?: string | null }) {
  return (
    <div className="student-activity__store">
      <RoleAvatar role="owner" size={32} src={photo} />
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
 * 진행 중은 GET /me/jobs?status=MATCHED (ADR 0032). 완료 · 성사되지 않은 일은 정산 내역
 * (GET /settlements)의 끝난 작업 (ADR 0042).
 */
function StudentActivityPage() {
  const navigate = useNavigate();
  // 「문의하기」는 그 작업의 채팅방으로 (못 찾으면 채팅 목록)
  const openJobChat = useOpenJobChat(STUDENT_PATHS.chat, STUDENT_PATHS.chats);
  const back = useBack(STUDENT_PATHS.me);
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.tab === params.get("tab"))?.tab ?? "applied";
  const { load: appliedLoad, reload: reloadApplied } = useAppliedJobs();
  const applied = appliedLoad.status === "loaded" ? appliedLoad.jobs : [];
  const { load: proposalsLoad, reload: reloadProposals } = useSentProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  // 완료 카드에 분야와 받은 후기 별점이 있어서 작업마다 함께 불러온다
  const { load: finishedLoad, reload: reloadFinished } = useFinishedJobs({ details: true, reviews: true });
  // 진행 중 카드에 가게 주소가 있어서 주소까지 불러온다
  const { load: progressLoad, reload: reloadProgress } = useProgressJobs({ storeAddress: true });
  // 완료 탭 위 정산 요약 (GET /settlements). 불러오지 못하면 요약 칸을 숨긴다
  const { load: settlementLoad } = useSettlementHistory();
  const [sheetJobId, setSheetJobId] = useState<number>();
  const sheetJob = applied.find((job) => job.jobId === sheetJobId);

  // 진행 중(GET /me/jobs?status=MATCHED)은 지금 지켜야 할 마감이 빠른 것부터
  const inProgress = (progressLoad.status === "loaded" ? progressLoad.jobs : [])
    .slice()
    .sort((a, b) => progressDeadline(a).due.localeCompare(progressDeadline(b).due));
  // 끝난 작업은 끝난 날 최신순
  const finished = finishedLoad.status === "loaded" ? finishedLoad.jobs : [];
  const done = finished.filter((job) => job.outcome === "completed");
  const canceled = finished.filter((job) => job.outcome === "canceled");
  // 지원한 의뢰 · 보낸 제안을 불러오는 중이거나 실패하면 개수 대신 null (요약 칸은 점 세 개)
  const counts: Record<StudentActivityTab, number | null> = {
    applied: appliedLoad.status === "loaded" ? applied.length : null,
    proposals: proposalsLoad.status === "loaded" ? proposals.length : null,
    inProgress: progressLoad.status === "loaded" ? inProgress.length : null,
    done: finishedLoad.status === "loaded" ? done.length : null,
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
    const sentOn = sentOnText(proposal.createdAt, proposal.rejectedAt);
    return (
      <li key={proposal.proposalId} className="student-activity__card">
        <CardHead
          kind="proposal"
          title={proposal.title}
          right={
            <>
              <span
                className={`student-activity__chip${
                  sentProposalInProgress(proposal.status, proposal.jobStatus) ? " student-activity__chip--working" : ""
                }`}
              >
                {sentProposalStatusLabel(proposal.status, proposal.jobStatus)}
              </span>
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
        <StoreLine
          name={proposal.store.storeName}
          address={storeAddressText(proposal.store.storeAddress)}
          photo={proposal.store.profileImageUrl}
        />
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

  const inProgressCard = (job: ProgressJob) => {
    const id = String(job.jobId);
    const deadline = progressDeadline(job);
    const detailPath =
      job.stage === "drafting"
        ? STUDENT_PATHS.workSubmit(id)
        : job.stage === "revising"
          ? STUDENT_PATHS.workRevision(id)
          : STUDENT_PATHS.workSubmitted(id);
    return (
      <li key={job.jobId} className="student-activity__card">
        <CardHead kind={job.kind} title={job.title} />
        <div className="student-activity__meta">
          {proposalBadgeNames(job.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
        </div>
        <p className="student-activity__line">{deadlineText(deadline.stage, deadline.due)}</p>
        <div className="student-activity__box">
          <span className="student-activity__dot" aria-hidden="true" />
          <span className="student-activity__status">{progressStatusText(job)}</span>
          <TextButton onClick={() => navigate(detailPath)}>상세보기</TextButton>
        </div>
        {job.storeName && (
          <>
            <div className="student-activity__divider" />
            <StoreLine name={job.storeName} address={job.storeAddress} photo={job.storePhoto} />
          </>
        )}
        <div className="student-activity__divider" />
        {job.stage === "drafting" && (
          <Button
            tone="student"
            size="medium"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.workSubmit(id))}
          >
            초안 제출하기
          </Button>
        )}
        {job.stage === "revising" && (
          <Button
            tone="student"
            size="medium"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.workRevisionSubmit(id))}
          >
            수정안 제출하기
          </Button>
        )}
        {job.stage === "submitted" && (
          <Button tone="student" size="medium" fullWidth onClick={() => openJobChat(job.jobId)}>
            문의하기
          </Button>
        )}
      </li>
    );
  };

  /** 「가게, 9월 27일 완료」 · 「가게, 8월 16일 성사되지 않음」 */
  const finishedLine = (job: FinishedJob, word: string) =>
    [job.storeName, job.closedOn ? `${formatMonthDay(job.closedOn)} ${word}` : word].filter(Boolean).join(", ");

  const doneCard = (job: FinishedJob) => {
    const id = String(job.jobId);
    return (
      <li key={job.jobId} className="student-activity__card">
        <CardHead kind={job.kind} title={job.title} right={<span className="student-activity__chip">완료</span>} />
        <div className="student-activity__meta">
          {proposalBadgeNames(job.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          <span>{finishedLine(job, "완료")}</span>
        </div>
        <p className="student-activity__line">작업비 {formatWon(job.amount)} 정산 완료</p>
        <div className="student-activity__divider" />
        <div className="student-activity__footer">
          <TextButton onClick={() => navigate(STUDENT_PATHS.workResult(id))}>내 결과물 보기</TextButton>
          {job.rating !== undefined && (
            <TextButton onClick={() => navigate(STUDENT_PATHS.workReview(id))}>
              받은 후기 ★ {job.rating.toFixed(1)}
            </TextButton>
          )}
        </div>
      </li>
    );
  };

  const canceledCard = (job: FinishedJob) => (
    <li key={job.jobId} className="student-activity__card">
      <CardHead
        kind={job.kind}
        title={job.title}
        right={<span className="student-activity__chip">성사되지 않음</span>}
      />
      <div className="student-activity__meta">
        {proposalBadgeNames(job.specialtyCategories).map((name) => (
          <CategoryBadge key={name} field={name} />
        ))}
        <span>{finishedLine(job, "성사되지 않음")}</span>
      </div>
      {job.amount > 0 && (
        <p className="student-activity__line">착수 보상 {formatWon(job.amount)} 정산 완료</p>
      )}
      <div className="student-activity__divider" />
      <div className="student-activity__footer">
        <TextButton onClick={() => navigate(STUDENT_PATHS.workCanceled(String(job.jobId)))}>상세보기</TextButton>
      </div>
    </li>
  );

  const listTitle = (label: string, count: number | null) => (
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
            {settlementLoad.status === "loaded" && (
              <SettlementSummaryBox summary={settlementSummaryOf(settlementLoad.data)} />
            )}
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
            layout="cards"
            status={appliedLoad.status}
            loadingText="지원한 의뢰를 불러오는 중이에요"
            errorText="지원한 의뢰를 불러오지 못했어요"
            onRetry={reloadApplied}
          />
        )}
        {tab === "inProgress" && progressLoad.status !== "loaded" && (
          <LoadNotice
            layout="cards"
            status={progressLoad.status}
            loadingText="진행 중인 작업을 불러오는 중이에요"
            errorText="진행 중인 작업을 불러오지 못했어요"
            onRetry={reloadProgress}
          />
        )}
        {tab === "proposals" && proposalsLoad.status !== "loaded" && (
          <LoadNotice
            layout="cards"
            status={proposalsLoad.status}
            loadingText="보낸 제안을 불러오는 중이에요"
            errorText="보낸 제안을 불러오지 못했어요"
            onRetry={reloadProposals}
          />
        )}
        {tab === "done" && finishedLoad.status !== "loaded" && (
          <LoadNotice
            layout="cards"
            status={finishedLoad.status}
            loadingText="끝난 작업을 불러오는 중이에요"
            errorText="끝난 작업을 불러오지 못했어요"
            onRetry={reloadFinished}
          />
        )}
        {counts[tab] === 0 && <p className="student-activity__empty">아직 없어요</p>}

        {tab === "done" && canceled.length > 0 && (
          <>
            {listTitle("성사되지 않은 일", canceled.length)}
            <ul className="student-activity__list">{canceled.map(canceledCard)}</ul>
          </>
        )}
      </div>

      <ApplicationSheet job={sheetJob} onClose={() => setSheetJobId(undefined)} />
    </SubScreen>
  );
}

export default StudentActivityPage;
