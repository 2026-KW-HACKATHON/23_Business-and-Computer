import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  AttachmentTiles,
  Button,
  CategoryBadge,
  Dialog,
  FlowBar,
  InfoRows,
  LabelChip,
  SubScreen,
  TextButton,
  WorkKindIcon,
} from "../components";
import { OWNER_PATHS, OwnerMissing, flowSteps, useOwnerRequest } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { formatMonthDay, formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerDetailPage.css";
import "./OwnerRequestPage.css";

/** 피그마 「보낸 의뢰>상세보기」. 모집 중인 의뢰서와 학생을 고른 뒤의 진행 안내 */
function OwnerRequestPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const request = useOwnerRequest(requestId);
  // 의뢰 취소 팝업: 확인 → 완료
  const [cancelStep, setCancelStep] = useState<"closed" | "confirm" | "done">("closed");

  if (!request) return <OwnerMissing title="보낸 의뢰" onBack={back} />;

  const applicantCount = request.applicants.length;
  const steps = [
    {
      title: `작업비 ${formatWon(request.budget)}을 안전결제로 맡겨요`,
      sub: "가꿈이 보관하다가 완료되면 학생에게 보내요",
    },
    { title: `${formatMonthDay(request.draftDue)}까지 초안이 도착해요` },
    {
      title: `수정을 요청하면 ${formatMonthDay(request.finalDue)}까지 최종본이 와요`,
      sub: `수정은 ${request.revisionLimit}회까지예요`,
    },
    {
      title: "완료를 확인하면 학생에게 정산돼요",
      sub: "결과물을 받고 7일 동안 답이 없으면 자동 완료",
    },
  ];

  return (
    <SubScreen
      title="보낸 의뢰"
      onBack={back}
      footer={
        <Button fullWidth onClick={back}>
          확인
        </Button>
      }
    >
      <div className="owner-detail">
        <div className="owner-detail__heading">
          <div className="owner-detail__title-row">
            <WorkKindIcon kind="request" size={28} />
            <h2 className="owner-detail__title">{request.title}</h2>
          </div>
          <div className="owner-detail__meta">
            <CategoryBadge field={request.field} />
            {applicantCount === 0
              ? "모집 중, 아직 지원자가 없어요"
              : `모집 중, 지원자 ${applicantCount}명`}
          </div>
        </div>

        <FlowBar steps={flowSteps("의뢰", 0, "모집 중")} />

        <div className="owner-request__terms">
          <InfoRows
            rows={[
              { label: "작업비", value: formatWon(request.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(request.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(request.finalDue) },
              { label: "수정", value: `${request.revisionLimit}회` },
            ]}
          />
          <TextButton className="owner-request__cancel" onClick={() => setCancelStep("confirm")}>
            의뢰 취소
          </TextButton>
        </div>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">할 일</h2>
          <div className="owner-request__tasks">
            {request.tasks.map((task) => (
              <LabelChip key={task} label={task} />
            ))}
          </div>
        </section>

        <section className="owner-detail__section">
          <h2 className="owner-detail__section-title">맡기고 싶은 일</h2>
          <p className="owner-detail__text">{request.description}</p>
        </section>

        {request.attachments.length > 0 && (
          <section className="owner-detail__section">
            <h2 className="owner-detail__section-title">참고 자료</h2>
            <AttachmentTiles names={request.attachments} height={110} />
          </section>
        )}

        <div className="owner-request__guide">
          <h2 className="owner-request__guide-title">학생을 고르면 이렇게 진행돼요</h2>
          <ol className="owner-request__guide-list">
            {steps.map((step, i) => (
              <li key={step.title} className="owner-request__guide-step">
                <span className="owner-request__guide-number">{i + 1}</span>
                <span className="owner-request__guide-text">
                  <span>{step.title}</span>
                  {step.sub && <small>{step.sub}</small>}
                </span>
              </li>
            ))}
          </ol>
        </div>
      </div>

      <Dialog
        open={cancelStep === "confirm"}
        image="warningOwner"
        title="의뢰를 취소할까요?"
        description="아직 학생을 고르기 전이라 결제한 작업비가 없어요. 지원한 학생에게는 취소 안내가 가요."
        onClose={() => setCancelStep("closed")}
        actions={
          <>
            <Button fullWidth onClick={() => setCancelStep("done")}>
              의뢰 취소하기
            </Button>
            <Button variant="secondary" fullWidth onClick={() => setCancelStep("closed")}>
              돌아가기
            </Button>
          </>
        }
      />
      <Dialog
        open={cancelStep === "done"}
        image="doneOwner"
        title="의뢰를 취소했어요"
        actions={
          <Button fullWidth onClick={() => navigate(OWNER_PATHS.activity("sent"), { replace: true })}>
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerRequestPage;
