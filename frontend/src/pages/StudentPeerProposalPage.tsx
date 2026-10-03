import { Navigate, useParams } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  FlowBar,
  RoleAvatar,
  SubScreen,
  WorkKindIcon,
} from "../components";
import {
  PEER_PROGRESS_LABEL,
  STUDENT_PATHS,
  StudentMissing,
  flowSteps,
  peerRecord,
  toggleEmpathy,
  usePeerProposal,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import "./StudentDetailPage.css";

/**
 * 피그마 「제안서 보기 (다른 학생 제안 · 공감)」. 다른 학생이 보낸 제안을 손님 입장에서 읽고
 * 공감한다. 공감은 수락을 기다리는 제안에만, 제안 하나에 한 번만 누를 수 있다.
 */
function StudentPeerProposalPage() {
  const { proposalId = "" } = useParams();
  const back = useBack(STUDENT_PATHS.explore);
  const proposal = usePeerProposal(proposalId);

  if (!proposal) return <StudentMissing title="제안서" onBack={back} />;
  // 내 제안에는 공감할 수 없으니 보낸 제안서로 보낸다
  if (proposal.mine) return <Navigate to={STUDENT_PATHS.proposal(proposal.id)} replace />;
  const { student } = proposal;
  const open = proposal.progress === "waitingAcceptance";

  return (
    <SubScreen
      title="제안서"
      onBack={back}
      footer={
        open ? (
          <Button
            tone="student"
            variant={proposal.empathized ? "secondary" : "primary"}
            fullWidth
            onClick={() => toggleEmpathy(proposal.id)}
          >
            {proposal.empathized
              ? `공감했어요 · ${proposal.empathyCount}명`
              : `공감하기 · ${proposal.empathyCount}명`}
          </Button>
        ) : (
          <Button tone="student" variant="secondary" fullWidth disabled>
            {proposal.progress === "accepted" ? "수락된 제안이에요" : "끝난 제안이에요"}
          </Button>
        )
      }
    >
      <div className="student-detail student-proposal">
        <p className="student-detail__notice">
          <b aria-hidden="true">ⓘ</b>
          다른 학생의 제안이에요. 손님으로서 공감되면 눌러 주세요.
        </p>

        <div className="student-detail__heading">
          <div className="student-detail__title-row">
            <WorkKindIcon kind="proposal" size={28} />
            <h2 className="student-detail__title">{proposal.title}</h2>
          </div>
          <div className="student-detail__meta">
            <CategoryBadge field={proposal.field} />
            {proposal.storeName} · {formatMonthDay(proposal.receivedOn)} ·{" "}
            {open ? "사장님 확인 중" : PEER_PROGRESS_LABEL[proposal.progress]}
          </div>
        </div>

        <FlowBar
          tone="student"
          steps={open ? flowSteps("제안", 0, "수락 대기") : flowSteps("제안", 1)}
        />

        <div className="student-proposal__empathy">
          <AppImage name="iconHeart" width={24} alt="" />
          <div>
            <strong className="student-proposal__empathy-title">
              학생 손님 {proposal.empathyCount}명이 공감했어요
            </strong>
            <p className="student-proposal__empathy-sub">
              가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
            </p>
          </div>
        </div>

        <div className="student-proposal__student">
          <RoleAvatar role="student" />
          <div className="student-proposal__student-info">
            <strong className="student-proposal__student-name">{student.name} 학생</strong>
            <span className="student-proposal__student-sub">
              {`${student.department} ${student.year}\n${peerRecord(student)}`}
            </span>
          </div>
        </div>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">손님 눈으로 본 문제</h2>
          <p className="student-detail__text">{proposal.problem}</p>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">이렇게 바꿔 드릴게요</h2>
          <p className="student-detail__text">{proposal.solution}</p>
        </section>

        <section className="student-detail__section">
          <h2 className="student-detail__section-title">참고 사진</h2>
          <AttachmentTiles names={proposal.attachments} />
        </section>

        <p className="student-detail__footnote">
          공감은 다시 누르면 취소돼요. 내 제안에는 누를 수 없어요.
        </p>
      </div>
    </SubScreen>
  );
}

export default StudentPeerProposalPage;
