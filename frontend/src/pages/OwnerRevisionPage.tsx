import { useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import {
  Button,
  Dialog,
  FormField,
  SubScreen,
  TextAreaField,
  WorkKindIcon,
} from "../components";
import {
  OWNER_PATHS,
  OwnerMissing,
  useOwnerWork,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatMonthDay } from "../lib/date";
import "./OwnerRevisionPage.css";

/** 피그마 「수정 요청」 + 「수정 요청 완료 팝업」. 남은 수정 횟수 안에서 고칠 곳을 한 번에 적는다 */
function OwnerRevisionPage() {
  const { workId = "" } = useParams();
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const work = useOwnerWork(workId);
  const [detail, setDetail] = useState("");
  const [photos, setPhotos] = useState<File[]>([]);
  const photoUrls = useObjectUrls(photos);
  const [sent, setSent] = useState(false);

  if (!work) return <OwnerMissing title="수정 요청" onBack={back} />;

  const remaining = work.revisionLimit - work.revisionCount;
  const arrived = work.revisionCount > 0 ? "수정안" : "초안";
  const meta = [
    `${work.student.name} 학생`,
    work.submittedOn && `${arrived} 도착 ${formatMonthDay(work.submittedOn)}`,
    `수정 ${work.revisionCount}/${work.revisionLimit}`,
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <SubScreen
      title="수정 요청"
      onBack={back}
      footer={
        <Button
          fullWidth
          disabled={remaining <= 0 || detail.trim() === ""}
          onClick={() => setSent(true)}
        >
          수정 요청 보내기
        </Button>
      }
    >
      <div className="owner-revision">
        <section className="owner-revision__work">
          <div className="owner-revision__work-head">
            <WorkKindIcon kind={work.kind} size={22} />
            <h2 className="owner-revision__work-title">{work.title}</h2>
          </div>
          <p className="owner-revision__meta">{meta}</p>
        </section>

        <div className="owner-revision__intro">
          <h2 className="owner-revision__title">어떤 부분을 고칠까요?</h2>
          <p className="owner-revision__description">
            {remaining > 0
              ? `수정은 ${remaining}회 남았어요. 한 번에 모아서 적어 주세요.`
              : "남은 수정이 없어요. 작업 확인에서 완료를 눌러 주세요."}
          </p>
        </div>

        <FormField label="자세히 적어 주세요" wrapsInput>
          <TextAreaField
            value={detail}
            maxLength={500}
            placeholder="예: 2번 게시물 사진이 어두워요. 조금 밝게 해 주세요."
            onChange={setDetail}
          />
        </FormField>

        <div className="owner-revision__photos">
          <label className="owner-revision__photo-button">
            <input
              type="file"
              accept="image/*"
              multiple
              className="owner-revision__photo-input"
              onChange={(e) => {
                const picked = Array.from(e.target.files ?? []).filter(
                  (file) => !photos.some((photo) => photo.name === file.name),
                );
                setPhotos([...photos, ...picked]);
                e.target.value = "";
              }}
            />
            + 참고 사진 올리기 (선택)
          </label>
          {photos.length > 0 && (
            <ul className="owner-revision__photo-list">
              {photos.map((photo, i) => (
                <li key={photo.name}>
                  <img className="owner-revision__thumb" src={photoUrls[i]} alt="" />
                  <span>{photo.name}</span>
                  <button
                    type="button"
                    aria-label={`${photo.name} 빼기`}
                    onClick={() => setPhotos(photos.filter((other) => other !== photo))}
                  >
                    ✕
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <p className="owner-revision__note">
          학생은 최종 마감({formatMonthDay(work.finalDue)})까지 수정본을 보내요
        </p>
      </div>

      <Dialog
        open={sent}
        image="doneOwner"
        title="수정 요청을 보냈어요"
        description={`${work.student.name} 학생이 ${formatMonthDay(work.finalDue)}까지\n수정본을 보내 드려요.`}
        actions={
          <Button
            fullWidth
            onClick={() => navigate(OWNER_PATHS.activity("inProgress"), { replace: true })}
          >
            확인
          </Button>
        }
      />
    </SubScreen>
  );
}

export default OwnerRevisionPage;
