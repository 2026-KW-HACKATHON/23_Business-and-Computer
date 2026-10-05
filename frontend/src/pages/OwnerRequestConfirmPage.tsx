import { useRef, useState } from "react";
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
import {
  OWNER_PATHS,
  readNewRequestState,
  registerOwnerRequest,
  taskSummary,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatMonthDayWeekday } from "../lib/date";
import { formatWon } from "../lib/money";
import "./OwnerRequestNewPage.css";
import "./OwnerRequestConfirmPage.css";

/** 사진이 없을 때 미리보기 훅에 넘기는 빈 목록 (매번 새 배열이면 주소를 다시 만든다) */
const NO_PHOTOS: File[] = [];

/** 피그마 「의뢰 등록 3/3 - 확인」. 2/3 에서 적은 내용을 의뢰서 모양으로 보여 준다 */
function OwnerRequestConfirmPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const back = useBack(OWNER_PATHS.newRequest);
  const state = readNewRequestState(location.state);
  const [registering, setRegistering] = useState(false);
  // 다시 그려지기 전에 두 번 눌러도 한 번만 올린다
  const registeredRef = useRef(false);
  const photoUrls = useObjectUrls(state?.content?.photos ?? NO_PHOTOS);

  if (!state?.content) return <Navigate to={OWNER_PATHS.newRequest} replace />;
  const { content } = state;

  // 백엔드 연동 전: 내 활동 · 홈의 보낸 의뢰에만 넣는다 (새로고침하면 사라짐)
  const register = () => {
    if (registeredRef.current) return;
    registeredRef.current = true;
    setRegistering(true);
    registerOwnerRequest({
      id: `req-new-${Date.now()}`,
      title: content.title,
      field: state.fields[0] ?? state.picked[0]?.field ?? "기타",
      budget: content.budget,
      draftDue: content.draftDue,
      finalDue: content.finalDue,
      revisionLimit: content.revisions,
      tasks: state.picked.map((p) => p.task),
      description: content.description,
      attachments: content.photos.map((photo) => photo.name),
      applicants: [],
    });
    navigate(OWNER_PATHS.newRequestDone, { replace: true });
  };

  return (
    <SubScreen
      title="의뢰 등록"
      onBack={back}
      footer={
        <Button fullWidth disabled={registering} onClick={register}>
          의뢰 등록하기
        </Button>
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
            {state.fields.map((field) => (
              <CategoryBadge key={field} field={field} />
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
              <AttachmentTiles
                names={content.photos.map((photo) => photo.name)}
                srcs={photoUrls}
                height={90}
              />
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
            지원한 학생 중 한 명을 고르면 작업비를 가꿈에 맡겨 두고, 작업이 끝나면 학생에게
            보내요. 의뢰는 월계1동 가게와 광운대 인증 학생에게만 보여요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default OwnerRequestConfirmPage;
