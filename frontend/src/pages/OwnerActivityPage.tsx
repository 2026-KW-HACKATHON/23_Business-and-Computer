import { useState } from "react";
import type { ReactNode } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  Dialog,
  EmpathyCount,
  LoadNotice,
  ReportSheet,
  RoleAvatar,
  SubScreen,
  SummaryCard,
  TextButton,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  PaymentSummaryBox,
  WorkPlanSheet,
  admissionYearText,
  deadlineText,
  ownerProgressDeadline,
  ownerProgressNoun,
  ownerProgressStatusText,
  progressWorkPlanContent,
  receivedOnText,
  receivedProposalStatusLabel,
  jobCategoryNames,
  studentMetaText,
  useOpenJobs,
  useOwnerPayments,
  useOwnerProgressJobs,
  useOwnerWorks,
  useReceivedProposals,
} from "../features/owner";
import type {
  ActivityTab,
  OpenJob,
  OwnerProgressJob,
  OwnerWork,
  ReceivedProposal,
  WorkPlanSheetContent,
} from "../features/owner";
import { proposalBadgeNames } from "../features/proposal";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import type { WorkKind } from "../types/workKind";
import "./OwnerActivityPage.css";
import { studentTitle } from "../lib/korean";

const TABS: { tab: ActivityTab; label: string }[] = [
  { tab: "sent", label: "보낸 의뢰" },
  { tab: "proposals", label: "받은 제안" },
  { tab: "inProgress", label: "진행 중" },
  { tab: "done", label: "완료" },
];

/** 학생 사진 + 이름 · 학번 · 학과 + 「프로필 보기」. 이름을 모르면 「학생」 */
function StudentLine({
  name,
  year,
  department,
  onProfile,
}: {
  name?: string;
  year?: string;
  department?: string;
  onProfile?: () => void;
}) {
  return (
    <div className="owner-activity__student">
      <RoleAvatar role="student" size={32} />
      <span className="owner-activity__student-info">
        <span className="owner-activity__student-name">
          <strong>{name ? studentTitle(name) : "학생"}</strong>
          {year && <span>{year}</span>}
        </span>
        {department && <span className="owner-activity__student-dept">{department}</span>}
      </span>
      {onProfile && <TextButton onClick={onProfile}>프로필 보기</TextButton>}
    </div>
  );
}

/** 카드 머리: 종류 아이콘 + 제목 + 오른쪽 (공감 수 · 완료 칩) */
function CardHead({ kind, title, right }: { kind: WorkKind; title: string; right?: ReactNode }) {
  return (
    <div className="owner-activity__head">
      <WorkKindIcon kind={kind} />
      <h3 className="owner-activity__title">{title}</h3>
      {right}
    </div>
  );
}

/**
 * 피그마 「내 활동 - 보낸 의뢰 · 받은 제안 · 진행 중 · 완료 (사장님)」.
 * 위 요약 카드 4칸이 탭이고, 고른 탭은 주소(?tab=)에 남아 돌아와도 그대로다.
 * 보낸 의뢰는 GET /me/jobs?status=OPEN (ADR 0030), 받은 제안은 GET /me/received-proposals (ADR 0025),
 * 진행 중은 GET /me/jobs?status=MATCHED (ADR 0035). 완료 탭은 아직 샘플 데이터다.
 */
function OwnerActivityPage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.me);
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.tab === params.get("tab"))?.tab ?? "sent";
  // 보낸 의뢰는 초안 마감, 진행 중은 지금 지킬 마감이 빠른 것부터
  const { load: openLoad, reload: reloadOpen } = useOpenJobs();
  const requests = [...(openLoad.status === "loaded" ? openLoad.data : [])].sort((a, b) =>
    a.draftDeadline.localeCompare(b.draftDeadline),
  );
  const { load: proposalsLoad, reload: reloadProposals } = useReceivedProposals();
  const proposals = proposalsLoad.status === "loaded" ? proposalsLoad.proposals : [];
  const { load: progressLoad, reload: reloadProgress } = useOwnerProgressJobs();
  const inProgress = (progressLoad.status === "loaded" ? [...progressLoad.jobs] : []).sort((a, b) =>
    ownerProgressDeadline(a).due.localeCompare(ownerProgressDeadline(b).due),
  );
  const works = useOwnerWorks();
  const { summary } = useOwnerPayments();
  const [planContent, setPlanContent] = useState<WorkPlanSheetContent>();
  const [reportTitle, setReportTitle] = useState<string>();
  // 맡은 학생의 프로필 API 가 없어서 「프로필 보기」는 곧 열린다는 안내
  const [profileSoon, setProfileSoon] = useState(false);

  const done = works.filter((w) => w.status === "completed");
  const canceled = works.filter((w) => w.status === "canceled");
  // 받은 제안을 불러오는 중이거나 실패하면 개수 대신 「-」
  const counts: Record<ActivityTab, number | string> = {
    sent: openLoad.status === "loaded" ? requests.length : "-",
    proposals: proposalsLoad.status === "loaded" ? proposals.length : "-",
    inProgress: progressLoad.status === "loaded" ? inProgress.length : "-",
    done: done.length,
  };
  const selectedIndex = TABS.findIndex((t) => t.tab === tab);

  const sentCard = (request: OpenJob) => {
    const applicants = request.applicantCount;
    const id = String(request.jobId);
    return (
      <li key={request.jobId} className="owner-activity__card">
        <CardHead kind="request" title={request.title} />
        <div className="owner-activity__meta">
          {jobCategoryNames(request.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          <span>{applicants > 0 ? `지원자 ${applicants}명` : "아직 지원자가 없어요"}</span>
          <TextButton
            className="owner-activity__push"
            onClick={() => navigate(OWNER_PATHS.request(id))}
          >
            상세 보기
          </TextButton>
        </div>
        <p className="owner-activity__line">{deadlineText("draft", request.draftDeadline)}</p>
        {applicants > 0 && (
          <>
            <div className="owner-activity__divider" />
            <Button
              size="medium"
              fullWidth
              onClick={() => navigate(OWNER_PATHS.requestApplicants(id))}
            >
              지원자 보기
            </Button>
          </>
        )}
      </li>
    );
  };

  // 모든 상태를 보인다
  const proposalCard = (proposal: ReceivedProposal) => {
    const openDetail = () => navigate(OWNER_PATHS.proposal(String(proposal.proposalId)));
    const receivedOn = receivedOnText(proposal.createdAt);
    return (
      <li key={proposal.proposalId} className="owner-activity__card">
        <CardHead
          kind="proposal"
          title={proposal.title}
          right={
            <>
              <span className="owner-activity__chip">
                {receivedProposalStatusLabel(proposal.status, proposal.jobStatus)}
              </span>
              <EmpathyCount count={proposal.likeCount} empathized />
            </>
          }
        />
        <div className="owner-activity__meta owner-activity__meta--wrap">
          {proposalBadgeNames(proposal.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          {receivedOn && <span>{receivedOn}</span>}
        </div>
        <div className="owner-activity__box owner-activity__box--column">
          <p className="owner-activity__excerpt">{proposal.proposedSolution}</p>
          <TextButton onClick={openDetail}>상세보기</TextButton>
        </div>
        <div className="owner-activity__divider" />
        <StudentLine
          name={proposal.student.name}
          department={studentMetaText(proposal.student.studentNumber, proposal.student.major)}
        />
        {proposal.status === "PENDING" && (
          <>
            <div className="owner-activity__divider" />
            <Button size="medium" fullWidth onClick={openDetail}>
              제안 받기
            </Button>
          </>
        )}
      </li>
    );
  };

  // 학생이 맡아 진행 중인 내 의뢰 (서버). 만드는 중이면 상세보기 = 지원서 바텀시트 또는 받은 제안
  const inProgressCard = (job: OwnerProgressJob) => {
    const id = String(job.jobId);
    const submitted = job.stage === "submitted";
    const noun = ownerProgressNoun(job);
    const deadline = ownerProgressDeadline(job);
    const plan = progressWorkPlanContent(job);
    const proposalId = job.proposalId;
    const openDetail = submitted
      ? () => navigate(OWNER_PATHS.workCheck(id))
      : plan
        ? () => setPlanContent(plan)
        : proposalId !== undefined
          ? () => navigate(OWNER_PATHS.proposal(String(proposalId)))
          : undefined;
    return (
      <li key={job.jobId} className="owner-activity__card">
        <CardHead kind={job.kind} title={job.title} />
        <div className="owner-activity__meta">
          {jobCategoryNames(job.specialtyCategories).map((name) => (
            <CategoryBadge key={name} field={name} />
          ))}
          <span>{deadlineText(deadline.stage, deadline.due)}</span>
        </div>
        <div className="owner-activity__box">
          <span className="owner-activity__dot" aria-hidden="true" />
          <span className="owner-activity__status">{ownerProgressStatusText(job)}</span>
          {openDetail && <TextButton onClick={openDetail}>상세보기</TextButton>}
        </div>
        <div className="owner-activity__divider" />
        <StudentLine
          name={job.student.name}
          year={admissionYearText(job.student.studentNumber)}
          department={job.student.major}
          onProfile={() => setProfileSoon(true)}
        />
        {submitted ? (
          <>
            <div className="owner-activity__divider" />
            <div className="owner-activity__actions">
              <Button onClick={() => navigate(OWNER_PATHS.workCheck(id))}>{noun} 확인하기</Button>
              <Button variant="secondary" size="medium" onClick={() => navigate(OWNER_PATHS.chats)}>
                문의하기
              </Button>
            </div>
          </>
        ) : (
          <div className="owner-activity__trouble">
            <span>문제가 있나요?</span>
            <TextButton onClick={() => navigate(OWNER_PATHS.workCancel(id))}>작업 취소</TextButton>
            <TextButton onClick={() => setReportTitle(job.title)}>문제 신고</TextButton>
          </div>
        )}
      </li>
    );
  };

  const doneCard = (work: OwnerWork) => {
    const auto = work.completedBy === "auto";
    const completedOn = work.completedOn ? formatMonthDay(work.completedOn) : "";
    return (
      <li key={work.id} className="owner-activity__card">
        <CardHead
          kind={work.kind}
          title={work.title}
          right={<span className="owner-activity__chip">{auto ? "자동 완료" : "완료"}</span>}
        />
        <div className="owner-activity__meta">
          <CategoryBadge field={work.field} />
          <span>
            {work.student.name} 학생, {completedOn} {auto ? "자동 완료" : "완료"}
          </span>
        </div>
        <p className="owner-activity__line">작업비 {formatWon(work.budget)} 정산 완료</p>
        <div className="owner-activity__divider" />
        <div className="owner-activity__footer">
          <TextButton onClick={() => navigate(OWNER_PATHS.workResult(work.id))}>결과물 보기</TextButton>
          {work.reviewed ? (
            <span className="owner-activity__reviewed">후기 작성 완료</span>
          ) : (
            <TextButton onClick={() => navigate(OWNER_PATHS.workReview(work.id))}>후기 남기기</TextButton>
          )}
        </div>
      </li>
    );
  };

  const canceledCard = (work: OwnerWork) => (
    <li key={work.id} className="owner-activity__card">
      <CardHead
        kind={work.kind}
        title={work.title}
        right={<span className="owner-activity__chip">성사되지 않음</span>}
      />
      <div className="owner-activity__meta">
        <CategoryBadge field={work.field} />
        <span>
          {work.student.name} 학생, {work.cancel ? formatMonthDay(work.cancel.canceledOn) : ""} 성사되지 않음
        </span>
      </div>
      {work.cancel && (
        <p className="owner-activity__line">
          작업비 {formatWon(work.budget)} 중 {formatWon(work.cancel.refund)} 환불
        </p>
      )}
      <div className="owner-activity__divider" />
      <div className="owner-activity__footer">
        <TextButton onClick={() => navigate(OWNER_PATHS.workCanceled(work.id))}>
          상세보기
        </TextButton>
      </div>
    </li>
  );

  const listTitle = (label: string, count: number | string) => (
    <h2 className="owner-activity__list-title">
      {label} <span>{count}</span>
    </h2>
  );

  return (
    <SubScreen title="내 활동" onBack={back}>
      <div className="owner-activity">
        <SummaryCard
          items={TABS.map(({ tab: t, label }) => ({ label, count: counts[t] }))}
          selectedIndex={selectedIndex}
          onSelect={(i) => setParams({ tab: TABS[i].tab }, { replace: true })}
        />

        {tab === "done" && (
          <section className="owner-activity__payments">
            <div className="owner-activity__payments-head">
              <h2 className="owner-activity__list-title">결제 내역</h2>
              <TextButton onClick={() => navigate(OWNER_PATHS.payments)}>전체 보기</TextButton>
            </div>
            <PaymentSummaryBox summary={summary} />
          </section>
        )}

        {listTitle(TABS[selectedIndex].label, counts[tab])}
        <ul className="owner-activity__list">
          {tab === "sent" && requests.map(sentCard)}
          {tab === "proposals" && proposals.map(proposalCard)}
          {tab === "inProgress" && inProgress.map(inProgressCard)}
          {tab === "done" && done.map(doneCard)}
        </ul>
        {tab === "sent" && openLoad.status !== "loaded" && (
          <LoadNotice
            status={openLoad.status === "loading" ? "loading" : "error"}
            loadingText="보낸 의뢰를 불러오는 중이에요"
            errorText="보낸 의뢰를 불러오지 못했어요"
            onRetry={reloadOpen}
          />
        )}
        {tab === "proposals" && proposalsLoad.status !== "loaded" && (
          <LoadNotice
            status={proposalsLoad.status}
            loadingText="받은 제안을 불러오는 중이에요"
            errorText="받은 제안을 불러오지 못했어요"
            onRetry={reloadProposals}
          />
        )}
        {tab === "inProgress" && progressLoad.status !== "loaded" && (
          <LoadNotice
            status={progressLoad.status}
            loadingText="진행 중인 작업을 불러오는 중이에요"
            errorText="진행 중인 작업을 불러오지 못했어요"
            onRetry={reloadProgress}
          />
        )}

        {tab === "done" && canceled.length > 0 && (
          <>
            {listTitle("성사되지 않은 일", canceled.length)}
            <ul className="owner-activity__list">{canceled.map(canceledCard)}</ul>
          </>
        )}
      </div>

      <WorkPlanSheet
        content={planContent}
        onClose={() => setPlanContent(undefined)}
        onChat={() => navigate(OWNER_PATHS.chats)}
      />
      <Dialog
        open={profileSoon}
        image="sorryOwner"
        title="학생 프로필은 곧 볼 수 있어요"
        description={"지금은 작업 중인 학생의 프로필을 열 수 없어요.\n준비되면 여기서 바로 볼 수 있어요."}
        onClose={() => setProfileSoon(false)}
        actions={
          <Button fullWidth onClick={() => setProfileSoon(false)}>
            확인
          </Button>
        }
      />
      <ReportSheet
        open={reportTitle !== undefined}
        workTitle={reportTitle ?? ""}
        onClose={() => setReportTitle(undefined)}
      />
    </SubScreen>
  );
}

export default OwnerActivityPage;
