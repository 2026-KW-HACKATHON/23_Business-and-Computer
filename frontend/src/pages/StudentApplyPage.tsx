import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Checkbox,
  Dialog,
  FormField,
  SubScreen,
  TextAreaField,
  TitleField,
  WorkKindIcon,
} from "../components";
import {
  STUDENT_PATHS,
  StudentMissing,
  applyToRequest,
  useStudentApplication,
  useStudentRequest,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatMonthDay } from "../lib/date";
import { formatWon } from "../lib/money";
import "./StudentDetailPage.css";
import "./StudentApplyPage.css";

/**
 * 피그마 「지원하기」. 작업계획서(한 줄 요약 · 작업 방법 · 결과물)를 쓰고
 * 마감 약속에 동의하면 보낸다. 마감은 사장님이 의뢰에서 정했다. 보내면 「지원 완료 팝업창」.
 */
function StudentApplyPage() {
  const { requestId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(STUDENT_PATHS.explore);
  const request = useStudentRequest(requestId);
  const applied = useStudentApplication(requestId) !== undefined;
  const [summary, setSummary] = useState("");
  const [method, setMethod] = useState("");
  const [deliverable, setDeliverable] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [sent, setSent] = useState(false);

  if (!request || request.progress !== "recruiting") {
    return <StudentMissing title="지원하기" onBack={back} message="지원할 수 없는 의뢰예요" />;
  }

  const canSend =
    !applied &&
    summary.trim() !== "" &&
    method.trim() !== "" &&
    deliverable.trim() !== "" &&
    agreed;

  const send = () => {
    applyToRequest(request.id, {
      summary: summary.trim(),
      method: method.trim(),
      deliverable: deliverable.trim(),
    });
    setSent(true);
  };

  return (
    <SubScreen
      title="지원하기"
      onBack={back}
      footer={
        <Button tone="student" fullWidth disabled={!canSend} onClick={send}>
          지원서 보내기
        </Button>
      }
    >
      <div className="student-detail">
        <div className="student-apply__request">
          <div className="student-detail__work-head">
            <WorkKindIcon kind="request" />
            <h2 className="student-detail__work-title">{request.title}</h2>
          </div>
          <p className="student-detail__work-meta">
            {request.store.name} · 작업비 {formatWon(request.budget)} · 수정 {request.revisionLimit}회
          </p>
          <p className="student-detail__work-meta">
            초안 마감 {formatMonthDay(request.draftDue)} · 최종 마감 {formatMonthDay(request.finalDue)}
          </p>
        </div>

        <div className="student-apply__intro">
          <h2 className="student-apply__title">작업계획서를 써 주세요</h2>
          <p className="student-apply__description">
            사장님은 전공·작업계획서·후기를 보고 학생을 골라요
          </p>
        </div>

        <FormField label="한 줄 요약" hint="카드에 가장 먼저 보여요" wrapsInput>
          <TitleField
            value={summary}
            placeholder="예: 메뉴 32개를 번역하고 검수까지 받을게요"
            onChange={setSummary}
          />
        </FormField>

        <FormField label="작업 방법" hint="어떻게 만들고 검수할지 적어 주세요" wrapsInput>
          <TextAreaField
            value={method}
            maxLength={500}
            placeholder="예: 메뉴 이름은 소리 나는 대로 적고, 아래에 재료와 맛을 한 줄로 설명해요"
            onChange={setMethod}
          />
        </FormField>

        <FormField label="결과물" hint="어떤 파일로 드릴지 적어 주세요" wrapsInput>
          <TextAreaField
            value={deliverable}
            maxLength={200}
            placeholder="예: 인쇄용 PDF와 바로 고칠 수 있는 원본 파일"
            onChange={setDeliverable}
          />
        </FormField>

        <Checkbox
          checked={agreed}
          onChange={setAgreed}
          label="선택되면 사장님이 정한 마감을 꼭 지킬게요 (필수)"
          description="연락이 끊기거나 마감을 넘기면 노쇼 페널티가 있어요"
        />
      </div>

      <Dialog
        open={sent}
        image="doneStudent"
        title="지원서를 보냈어요"
        description={"사장님이 작업계획서를 확인하면\n결과를 알림으로 알려 드려요."}
        actions={
          <Button
            tone="student"
            fullWidth
            onClick={() => navigate(STUDENT_PATHS.activity("applied"), { replace: true })}
          >
            지원 현황 보기
          </Button>
        }
      />
    </SubScreen>
  );
}

export default StudentApplyPage;
