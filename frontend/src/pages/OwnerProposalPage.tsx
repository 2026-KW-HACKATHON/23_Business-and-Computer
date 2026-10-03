import { useNavigate, useParams } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  FlowBar,
  InfoRows,
  RoleAvatar,
  SubScreen,
  TextButton,
  WorkKindIcon,
  WorkPlan,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  studentRecord,
  useOwnerProposal,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerProposalPage.css";

/** 피그마 「받은 제안 상세」. 「거절하기」 · 「의뢰하기」 */
function OwnerProposalPage() {
  const { proposalId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const proposal = useOwnerProposal(proposalId);

  if (!proposal) return <OwnerMissing title="받은 제안" onBack={back} />;

  const { student } = proposal;

  return (
    <SubScreen
      title="받은 제안"
      onBack={back}
      footer={
        <div className="owner-detail__actions">
          {/* 거절은 백엔드 연동 때 제안을 REJECTED 로 바꾸고 돌아간다 */}
          <Button variant="secondary" onClick={back}>
            거절하기
          </Button>
          <Button onClick={() => navigate(OWNER_PATHS.proposalAccept(proposal.id))}>의뢰하기</Button>
        </div>
      }
    >
      <div className="owner-detail owner-proposal">
        <div className="owner-detail__heading">
          <div className="owner-detail__title-row">
            <WorkKindIcon kind="proposal" size={28} />
            <h2 className="owner-detail__title">{proposal.title}</h2>
          </div>
          <div className="owner-detail__meta">
            <CategoryBadge field={proposal.field} />
            {formatMonthDay(proposal.receivedOn)} 도착
          </div>
        </div>

        <FlowBar steps={flowSteps("제안", 0, "결정해 주세요")} />

        <div className="owner-proposal__empathy">
          <AppImage name="iconHeart" width={24} alt="" />
          <div>
            <strong className="owner-proposal__empathy-title">
              광운대생 손님 {proposal.empathyCount}명이 공감했어요
            </strong>
            <p className="owner-proposal__empathy-sub">
              가게를 이용하는 학생들도 필요하다고 느낀 제안이에요
            </p>
          </div>
        </div>

        <div className="owner-proposal__student">
          <RoleAvatar role="student" />
          <div className="owner-proposal__student-info">
            <strong className="owner-proposal__student-name">{student.name} 학생</strong>
            <span className="owner-proposal__student-sub">
              {`${student.department} ${student.year}\n${studentRecord(student)}`}
            </span>
          </div>
          <TextButton onClick={() => navigate(OWNER_PATHS.student(student.id))}>프로필 보기</TextButton>
        </div>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">손님 눈으로 본 문제</h2>
          <p className="owner-detail__text">{proposal.problem}</p>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">이렇게 바꿔 드릴게요</h2>
          <p className="owner-detail__text">{proposal.solution}</p>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">작업계획서</h2>
          <WorkPlan text={proposal.plan} />
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">희망 작업비 · 예상 기간</h2>
          <div className="owner-detail__box">
            <InfoRows
              size="large"
              rows={[
                { label: "희망 작업비", value: formatWon(proposal.wishBudget) },
                { label: "예상 기간", value: `${proposal.expectedDays}일` },
              ]}
            />
          </div>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">참고 사진</h2>
          <AttachmentTiles names={proposal.attachments} />
        </section>

        <p className="owner-detail__footnote">
          「의뢰하기」를 누르면 이 제안과 희망 작업비를 바탕으로 의뢰서를 만들어요. 마감일과 수정
          횟수는 그때 정해요.
        </p>
      </div>
    </SubScreen>
  );
}

export default OwnerProposalPage;
