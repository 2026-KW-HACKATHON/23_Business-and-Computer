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
  OWNER_PATHS,
  PaymentSummaryBox,
  ReportSheet,
  WorkPlanSheet,
  deadlineText,
  useOwnerPayments,
  useOwnerProposals,
  useOwnerRequests,
  useOwnerWorks,
} from "../features/owner";
import type { ActivityTab, OwnerProposal, OwnerRequest, OwnerWork } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import type { WorkKind } from "../types/workKind";
import "./OwnerActivityPage.css";

const TABS: { tab: ActivityTab; label: string }[] = [
  { tab: "sent", label: "보낸 의뢰" },
  { tab: "proposals", label: "받은 제안" },
  { tab: "inProgress", label: "진행 중" },
  { tab: "done", label: "완료" },
];

/** 학생 사진 + 이름 · 학번 · 학과 + 「프로필 보기」 */
function StudentLine({
  name,
  year,
  department,
  onProfile,
}: {
  name: string;
  year?: string;
  department?: string;
  onProfile?: () => void;
}) {
  return (
    <div className="owner-activity__student">
      <RoleAvatar role="student" size={32} />
      <span className="owner-activity__student-info">
        <span className="owner-activity__student-name">
          <strong>{name} 학생</strong>
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
 */
function OwnerActivityPage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.me);
  const [params, setParams] = useSearchParams();
  const tab = TABS.find((t) => t.tab === params.get("tab"))?.tab ?? "sent";
  const requests = useOwnerRequests();
  const proposals = useOwnerProposals();
  const works = useOwnerWorks();
  const { summary } = useOwnerPayments();
  const [planWork, setPlanWork] = useState<OwnerWork>();
  const [reportWork, setReportWork] = useState<OwnerWork>();

  const inProgress = works.filter((w) => w.status === "inProgress" || w.status === "submitted");
  const done = works.filter((w) => w.status === "completed");
  const canceled = works.filter((w) => w.status === "canceled");
  const counts: Record<ActivityTab, number> = {
    sent: requests.length,
    proposals: proposals.length,
    inProgress: inProgress.length,
    done: done.length,
  };
  const selectedIndex = TABS.findIndex((t) => t.tab === tab);

  const sentCard = (request: OwnerRequest) => {
    const applicants = request.applicants.length;
    return (
      <li key={request.id} className="owner-activity__card">
        <CardHead kind="request" title={request.title} />
        <div className="owner-activity__meta">
          <CategoryBadge field={request.field} />
          <span>{applicants > 0 ? `지원자 ${applicants}명` : "아직 지원자가 없어요"}</span>
          <TextButton
            className="owner-activity__push"
            onClick={() => navigate(OWNER_PATHS.request(request.id))}
          >
            상세 보기
          </TextButton>
        </div>
        <p className="owner-activity__line">{deadlineText("draft", request.draftDue)}</p>
        {applicants > 0 && (
          <>
            <div className="owner-activity__divider" />
            <Button
              size="medium"
              fullWidth
              onClick={() => navigate(OWNER_PATHS.requestApplicants(request.id))}
            >
              지원자 보기
            </Button>
          </>
        )}
      </li>
    );
  };

  const proposalCard = (proposal: OwnerProposal) => (
    <li key={proposal.id} className="owner-activity__card">
      <CardHead
        kind="proposal"
        title={proposal.title}
        right={<EmpathyCount count={proposal.empathyCount} empathized />}
      />
      <div className="owner-activity__meta">
        <CategoryBadge field={proposal.field} />
      </div>
      <div className="owner-activity__box owner-activity__box--column">
        <p className="owner-activity__excerpt">{proposal.problem}</p>
        <TextButton onClick={() => navigate(OWNER_PATHS.proposal(proposal.id))}>상세보기</TextButton>
      </div>
      <div className="owner-activity__divider" />
      <StudentLine
        name={proposal.student.name}
        year={proposal.student.year}
        department={proposal.student.department}
        onProfile={() => navigate(OWNER_PATHS.student(proposal.student.id))}
      />
      <div className="owner-activity__divider" />
      <div className="owner-activity__actions">
        <Button size="medium" onClick={() => navigate(OWNER_PATHS.proposal(proposal.id))}>
          제안 받기
        </Button>
        {/* 거절은 백엔드 연동 때 제안을 REJECTED 로 바꾼다 */}
        <Button variant="secondary" size="medium">
          거절하기
        </Button>
      </div>
    </li>
  );

  const inProgressCard = (work: OwnerWork) => {
    const submitted = work.status === "submitted";
    const revised = work.revisionCount > 0;
    const stage = revised ? "수정안" : "초안";
    const deadline =
      submitted || revised
        ? deadlineText("final", work.finalDue)
        : deadlineText("draft", work.draftDue);
    const { student } = work;
    const studentId = student.id;
    return (
      <li key={work.id} className="owner-activity__card">
        <CardHead kind={work.kind} title={work.title} />
        <div className="owner-activity__meta">
          <CategoryBadge field={work.field} />
          <span>{deadline}</span>
        </div>
        <div className="owner-activity__box">
          <span className="owner-activity__dot" aria-hidden="true" />
          <span className="owner-activity__status">
            {submitted ? `${stage}이 도착했어요` : `${stage} 제작 중`}
          </span>
          <TextButton
            onClick={() =>
              submitted ? navigate(OWNER_PATHS.workCheck(work.id)) : setPlanWork(work)
            }
          >
            상세보기
          </TextButton>
        </div>
        <div className="owner-activity__divider" />
        <StudentLine
          name={student.name}
          year={student.year}
          department={student.department}
          onProfile={studentId ? () => navigate(OWNER_PATHS.student(studentId)) : undefined}
        />
        {submitted ? (
          <>
            <div className="owner-activity__divider" />
            <div className="owner-activity__actions">
              <Button onClick={() => navigate(OWNER_PATHS.workCheck(work.id))}>
                {stage} 확인하기
              </Button>
              <Button
                variant="secondary"
                size="medium"
                onClick={() => navigate(OWNER_PATHS.chat(work.id))}
              >
                문의하기
              </Button>
            </div>
          </>
        ) : (
          <div className="owner-activity__trouble">
            <span>문제가 있나요?</span>
            <TextButton onClick={() => navigate(OWNER_PATHS.workCancel(work.id))}>작업 취소</TextButton>
            <TextButton onClick={() => setReportWork(work)}>문제 신고</TextButton>
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
        right={<span className="owner-activity__chip">취소됨</span>}
      />
      <div className="owner-activity__meta">
        <CategoryBadge field={work.field} />
        <span>
          {work.student.name} 학생, {work.cancel ? formatMonthDay(work.cancel.canceledOn) : ""}{" "}
          {work.cancel?.stage === "inProgress" ? "작업 중 취소" : "시작 전 취소"}
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
          취소 상세보기
        </TextButton>
      </div>
    </li>
  );

  const listTitle = (label: string, count: number) => (
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

        {tab === "done" && canceled.length > 0 && (
          <>
            {listTitle("취소된 일", canceled.length)}
            <ul className="owner-activity__list">{canceled.map(canceledCard)}</ul>
          </>
        )}
      </div>

      <WorkPlanSheet
        work={planWork}
        onClose={() => setPlanWork(undefined)}
        onChat={() => planWork && navigate(OWNER_PATHS.chat(planWork.id))}
      />
      <ReportSheet
        open={reportWork !== undefined}
        workTitle={reportWork?.title ?? ""}
        onClose={() => setReportWork(undefined)}
      />
    </SubScreen>
  );
}

export default OwnerActivityPage;
