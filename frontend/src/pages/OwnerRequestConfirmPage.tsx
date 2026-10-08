import { useEffect, useRef, useState } from "react";
import { Navigate, useLocation, useNavigate } from "react-router-dom";
import {
  AppImage,
  Button,
  CategoryBadge,
  InfoRows,
  ReferencePhotos,
  StepIndicator,
  SubScreen,
  TextButton,
} from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  readNewRequestState,
  requestCategoryNames,
  sendJobCreate,
  taskSummary,
  toJobCreateRequest,
  uploadRequestPhoto,
} from "../features/owner";
import type { NewRequestState } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { useFinishFlow } from "../hooks/useFlowHistory";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatMonthDayWeekday } from "../lib/date";
import { FLOW_KEYS } from "../lib/flowHistory";
import { formatWon } from "../lib/money";
import "./OwnerRequestNewPage.css";
import "./OwnerRequestConfirmPage.css";

/** 사진이 없을 때 미리보기 훅에 넘기는 빈 목록 (매번 새 배열이면 주소를 다시 만든다) */
const NO_PHOTOS: File[] = [];

type SendError = "photo" | "invalidInput" | "dataConflict" | "retry";

const SEND_ERROR_TEXT: Record<SendError, string> = {
  photo: "사진을 올리지 못했어요. 다시 시도해 주세요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  dataConflict: "일시적인 문제가 생겼어요. 다시 시도해도 안 되면 문의해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「의뢰 등록 3/3 - 확인」. 2/3 에서 적은 내용을 의뢰서 모양으로 보여 주고, 「의뢰 등록하기」에서
 * 참고 사진을 올린 뒤 1/3 에서 고른 특기 id 로 POST /jobs 등록한다 (ADR 0028, ADR 0058).
 */
function OwnerRequestConfirmPage() {
  const navigate = useNavigate();
  const finishFlow = useFinishFlow();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.newRequest);
  const state = readNewRequestState(location.state);
  // 요청 중 여부와 오류는 이 화면에만 둔다 (학생 제안 4/4 와 같은 방식)
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<SendError | null>(null);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 등록한다
  const inFlight = useRef(false);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);
  // 다시 시도할 때 이미 올린 사진은 또 올리지 않는다
  const uploaded = useRef(new Map<File, string>());
  const photoUrls = useObjectUrls(state?.content?.photos ?? NO_PHOTOS);

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  if (!state?.content || state.picked.length === 0) {
    return <Navigate to={OWNER_PATHS.newRequest} replace />;
  }
  const { content } = state;

  // 할 일 목록이 바뀌었으면 1/3 부터 다시 고른다
  const backToTasks = () => {
    window.alert("할 일 목록이 바뀌었어요. 다시 골라 주세요");
    const next: NewRequestState = { ...state, categoryIds: [], picked: [] };
    navigate(OWNER_PATHS.newRequest, { replace: true, state: next });
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
      const upload = await uploadRequestPhoto(photo);
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

    const result = await sendJobCreate(toJobCreateRequest(content, state.picked, imageUrls));
    if (id !== requestId.current) return;
    setSending(false);

    switch (result.status) {
      case "created":
        // 1/3 ~ 3/3 은 방문 기록에서 지워, 등록한 뒤 뒤로가기로 다시 등록하지 못한다
        finishFlow(FLOW_KEYS.requestNew, OWNER_PATHS.newRequestDone);
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "notOwner":
        window.alert("사장님만 의뢰를 등록할 수 있어요");
        navigate(landingPath(), { replace: true });
        break;
      case "specialtyInvalid":
        backToTasks();
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

  const register = async () => {
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
      title="의뢰 등록"
      onBack={back}
      footer={
        <>
          {sendError && (
            <p className="owner-confirm__send-error" role="alert">
              {SEND_ERROR_TEXT[sendError]}
            </p>
          )}
          <Button
            loading={sending}
            loadingLabel="등록하는 중"
            fullWidth
            disabled={sending}
            onClick={() => void register()}
          >
            의뢰 등록하기
          </Button>
        </>
      }
    >
      <div className="owner-new">
        <StepIndicator total={3} current={3} />

        <div className="owner-new__intro">
          <h2 className="owner-new__title">의뢰서를 확인해 주세요</h2>
          <p className="owner-new__description">
            등록하면 광운대 인증 학생들이 작업계획서를 써서 지원해요
          </p>
        </div>

        <article className="owner-confirm__card">
          <div className="owner-confirm__head">
            <AppImage name="iconCardRequest" width={24} height={24} />
            <h3 className="owner-confirm__title">{content.title}</h3>
          </div>
          <div className="owner-confirm__badges">
            {requestCategoryNames(state).map((name) => (
              <CategoryBadge key={name} field={name} />
            ))}
          </div>
          <InfoRows
            rows={[
              { label: "할 일", value: taskSummary(state) },
              { label: "작업비", value: formatWon(content.budget) },
              { label: "초안 마감", value: formatMonthDayWeekday(content.draftDue) },
              { label: "최종 마감", value: formatMonthDayWeekday(content.finalDue) },
              { label: "수정", value: `${content.revisions}회` },
            ]}
          />
          <hr className="owner-confirm__divider" />
          <div className="owner-confirm__text">
            <p className="owner-confirm__text-title">맡기고 싶은 일</p>
            <p className="owner-confirm__text-body">{content.description}</p>
          </div>
          {content.photos.length > 0 && (
            <div className="owner-confirm__text">
              <p className="owner-confirm__text-title">참고 사진</p>
              <ReferencePhotos urls={photoUrls} names={content.photos.map((photo) => photo.name)} />
            </div>
          )}
          <hr className="owner-confirm__divider" />
          <TextButton className="owner-confirm__edit" onClick={back}>
            내용 고치기
          </TextButton>
        </article>

        <div className="owner-confirm__notice">
          <strong>결제는 학생을 고른 뒤에 해요</strong>
          <p>
            지원한 학생 중 한 명을 고르면 작업비를 골목인턴에 맡겨 두고, 작업이 끝나면 학생에게
            보내요. 의뢰는 월계1동 가게와 광운대 인증 학생에게만 보여요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default OwnerRequestConfirmPage;
