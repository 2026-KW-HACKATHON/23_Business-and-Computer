import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  AppImage,
  AttachmentTiles,
  Button,
  CategoryBadge,
  InfoRows,
  StepIndicator,
  SubScreen,
  TextButton,
} from "../components";
import { landingPath } from "../features/auth";
import {
  STUDENT_PATHS,
  expectedDaysText,
  proposalCategoryNames,
  proposalTaskSummary,
  readNewProposalState,
  sendProposalRequest,
  toProposalRequest,
  uploadProposalPhoto,
} from "../features/student";
import type { NewProposalState } from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";
import { FIELDS } from "../types/field";
import type { Field } from "../types/field";
import "./StudentProposalNewPage.css";

type SendError = "photo" | "invalidInput" | "dataConflict" | "retry";

const SEND_ERROR_TEXT: Record<SendError, string> = {
  photo: "사진을 올리지 못했어요. 다시 시도해 주세요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  dataConflict: "일시적인 문제가 생겼어요. 다시 시도해도 안 되면 문의해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「제안 보내기 4/4 - 확인」. 3/4 에서 적은 내용을 제안서 모양으로 보여 주고,
 * 「제안 보내기」에서 참고 사진을 올린 뒤 POST /proposals 로 보낸다 (ADR 0020).
 */
function StudentProposalConfirmPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(STUDENT_PATHS.newProposal);
  const state = readNewProposalState(location.state);
  // 요청 중 여부와 오류는 이 화면에만 둔다 (학생 가입 3/3 과 같은 방식)
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<SendError | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 보낸다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);
  // 다시 시도할 때 이미 올린 사진은 또 올리지 않는다
  const uploaded = useRef(new Map<File, string>());

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (!state?.content || !state.store || state.picked.length === 0) {
    return <Navigate to={STUDENT_PATHS.newProposal} replace />;
  }
  const { content, store } = state;

  // 2/4 · 1/4 로 돌려보낼 때 나머지 값은 남긴다
  const backTo = (step: string, patch: Partial<NewProposalState>) => {
    navigate(step, { replace: true, state: { ...state, ...patch } });
  };

  const submit = async () => {
    const id = ++requestId.current;
    setSending(true);
    setSendError(null);

    const imageUrls: string[] = [];
    for (const photo of content.photos) {
      const cached = uploaded.current.get(photo);
      if (cached) {
        imageUrls.push(cached);
        continue;
      }
      const upload = await uploadProposalPhoto(photo);
      if (id !== requestId.current) return;
      if (upload.status === "unauthorized") {
        navigate("/login", { replace: true });
        return;
      }
      if (upload.status === "failed") {
        setSending(false);
        setSendError("photo");
        return;
      }
      uploaded.current.set(photo, upload.imageUrl);
      imageUrls.push(upload.imageUrl);
    }

    const result = await sendProposalRequest(toProposalRequest(store, state.picked, content, imageUrls));
    if (id !== requestId.current) return;
    setSending(false);

    switch (result.status) {
      case "sent":
        navigate(STUDENT_PATHS.newProposalDone, { replace: true });
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "notStudent":
        window.alert("학생만 제안을 보낼 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "storeGone":
        window.alert("가게 정보가 바뀌었어요. 가게를 다시 골라 주세요");
        backTo(STUDENT_PATHS.newProposal, { store: undefined });
        break;
      case "specialtyInvalid":
        window.alert("할 일 목록이 바뀌었어요. 다시 골라 주세요");
        backTo(STUDENT_PATHS.newProposalStep(2), { categoryIds: [], picked: [] });
        break;
      case "photoInvalid":
        // 서버가 받지 않은 사진 주소는 버리고, 다시 누르면 새로 올린다
        uploaded.current.clear();
        setSendError("photo");
        break;
      case "invalidInput":
      case "dataConflict":
        setSendError(result.status);
        break;
      default:
        setSendError("retry");
    }
  };

  const send = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      await submit();
    } finally {
      inFlight.current = false;
    }
  };

  return (
    <SubScreen
      title="제안 보내기"
      onBack={back}
      footer={
        <>
          {sendError && (
            <p className="student-new__send-error" role="alert">
              {SEND_ERROR_TEXT[sendError]}
            </p>
          )}
          <Button tone="student" fullWidth disabled={sending} onClick={() => void send()}>
            {sending ? "보내는 중..." : "제안 보내기"}
          </Button>
        </>
      }
    >
      <div className="student-new">
        <StepIndicator total={4} current={4} tone="student" />

        <div className="student-new__intro">
          <h2 className="student-new__title">제안서를 확인해 주세요</h2>
          <p className="student-new__description">
            보내면 사장님께 알림이 가고, 다른 광운대생에게도 공개돼요
          </p>
        </div>

        <article className="student-confirm__card">
          <div className="student-confirm__head">
            <AppImage name="iconCardProposal" width={24} height={24} />
            <h3 className="student-confirm__title">{content.title}</h3>
          </div>
          <div className="student-confirm__badges">
            {/* 분류 이름이 피그마 분야와 같을 때만 뱃지로 보인다 */}
            {proposalCategoryNames(state)
              .map((name) => FIELDS.find((field) => field === name))
              .filter((field): field is Field => field !== undefined)
              .map((field) => (
                <CategoryBadge key={field} field={field} />
              ))}
          </div>
          <InfoRows
            rows={[
              { label: "할 일", value: proposalTaskSummary(state) },
              { label: "받는 가게", value: store.name },
              { label: "희망 작업비", value: formatWon(content.wishBudget) },
              { label: "예상 기간", value: expectedDaysText(content.draftDays, content.finalDays) },
            ]}
          />
          <hr className="student-confirm__divider" />
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">손님 눈으로 본 문제</p>
            <p className="student-confirm__text-body">{content.problem}</p>
          </div>
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">이렇게 바꿔 드릴게요</p>
            <p className="student-confirm__text-body">{content.solution}</p>
          </div>
          <div className="student-confirm__text">
            <p className="student-confirm__text-title">작업계획서</p>
            <p className="student-confirm__text-body">{content.plan}</p>
          </div>
          {content.photos.length > 0 && (
            <AttachmentTiles names={content.photos.map((p) => p.name)} height={90} />
          )}
          <hr className="student-confirm__divider" />
          <TextButton className="student-confirm__edit" onClick={back}>
            내용 고치기
          </TextButton>
        </article>

        <div className="student-confirm__notice">
          <strong>사장님이 수락하면 의뢰서가 와요</strong>
          <p>
            사장님이 작업비·마감일·수정 횟수를 정해 의뢰서를 보내요. 내가 확인하고 동의하면 작업이
            시작돼요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default StudentProposalConfirmPage;
