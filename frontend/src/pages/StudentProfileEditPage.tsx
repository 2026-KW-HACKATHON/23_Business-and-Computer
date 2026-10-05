import { useEffect, useMemo, useRef, useState } from "react";
import { useLocation } from "react-router-dom";
import { Button, Chip, ProfilePhoto, SubScreen, TextField } from "../components";
import {
  STUDENT_PATHS,
  saveMyProfile,
  setMyProfilePhoto,
  useMyProfile,
  useMyProfilePhoto,
} from "../features/student";
import type { ProfileEditSection } from "../features/student";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { formatDotDate } from "../lib/date";
import { MAX_SPECIALTY_BADGES, SPECIALTY_BADGES } from "../types/specialty";
import "./StudentProfileEditPage.css";

interface CertificateRow {
  name: string;
  /** 「2023.08」처럼 적는다 */
  acquired: string;
}

/** 「2023.08」 · 「2023-8」 → 「2023-08」. 알아볼 수 없으면 비운다 */
function toYearMonth(text: string): string | undefined {
  const match = /^(\d{4})\s*[.\-/]\s*(\d{1,2})\.?$/.exec(text.trim());
  if (!match) return undefined;
  const month = Number(match[2]);
  if (month < 1 || month > 12) return undefined;
  return `${match[1]}-${String(month).padStart(2, "0")}`;
}

/**
 * 피그마 「프로필 편집(학생)」. 프로필 수정의 「기본 정보 수정」 · 각 「수정」에서 열린다.
 * 이름 · 학교 · 학과는 인증 정보라 보여 주기만 한다.
 * 백엔드 연동 전까지 저장한 값은 새로고침하면 처음으로 돌아간다.
 */
function StudentProfileEditPage() {
  const back = useBack(STUDENT_PATHS.profile);
  const location = useLocation();
  const section = (location.state as { section?: ProfileEditSection } | null)?.section;
  const profile = useMyProfile();
  const savedPhoto = useMyProfilePhoto();
  const [photo, setPhoto] = useState(savedPhoto);
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [intro, setIntro] = useState(profile.intro);
  const [badges, setBadges] = useState(profile.badges);
  const [limitReached, setLimitReached] = useState(false);
  const [certificates, setCertificates] = useState<CertificateRow[]>(() =>
    profile.certificates.map((c) => ({ name: c.name, acquired: formatDotDate(c.acquiredOn) })),
  );
  const [portfolioUrl, setPortfolioUrl] = useState(profile.portfolioUrl ?? "");
  const badgesRef = useRef<HTMLElement>(null);
  const certificatesRef = useRef<HTMLElement>(null);

  // 「전공역량 수정」 · 「자격증·포트폴리오 수정」으로 들어오면 그 칸부터 보여 준다
  useEffect(() => {
    const target = section === "badges" ? badgesRef : section === "certificates" ? certificatesRef : null;
    target?.current?.scrollIntoView({ block: "start" });
  }, [section]);

  const toggleBadge = (badge: string) => {
    if (badges.includes(badge)) {
      setBadges(badges.filter((b) => b !== badge));
      setLimitReached(false);
      return;
    }
    // 이미 5개면 고르지 않고 안내 문구로 알린다
    if (badges.length >= MAX_SPECIALTY_BADGES) {
      setLimitReached(true);
      return;
    }
    setBadges([...badges, badge]);
  };

  const editCertificate = (index: number, patch: Partial<CertificateRow>) => {
    setCertificates((rows) => rows.map((row, i) => (i === index ? { ...row, ...patch } : row)));
  };

  // 이름을 적은 줄만 저장한다. 연월을 알아볼 수 없으면 그 줄에 안내를 띄운다
  const filledCertificates = certificates.filter((c) => c.name.trim() !== "");
  const dateInvalid = (row: CertificateRow) =>
    row.name.trim() !== "" && toYearMonth(row.acquired) === undefined;
  const canSave = badges.length > 0 && !filledCertificates.some(dateInvalid);

  const handleSave = () => {
    if (!canSave) return;
    saveMyProfile({
      intro: intro.trim(),
      badges,
      certificates: filledCertificates.map((c) => ({
        name: c.name.trim(),
        acquiredOn: toYearMonth(c.acquired) ?? "",
      })),
      portfolioUrl: portfolioUrl.trim().replace(/^https?:\/\//, "") || undefined,
    });
    if (photo !== savedPhoto) setMyProfilePhoto(photo);
    back();
  };

  return (
    <SubScreen
      title="프로필 편집"
      onBack={back}
      footer={
        <Button fullWidth tone="student" disabled={!canSave} onClick={handleSave}>
          저장하기
        </Button>
      }
    >
      <div className="student-profile-edit">
        <header className="student-profile-edit__head">
          <ProfilePhoto size="medium" src={photoUrl} onSelect={setPhoto} />
          <h2 className="student-profile-edit__name">{profile.name} 학생</h2>
          <p className="student-profile-edit__school">
            광운대 {profile.department} {profile.year}
          </p>
        </header>

        <label className="student-profile-edit__field">
          <span className="student-profile-edit__label">한 줄 소개</span>
          <TextField
            placeholder="나를 표현하는 한 줄 소개"
            value={intro}
            onChange={(e) => setIntro(e.target.value)}
          />
        </label>

        <section ref={badgesRef} className="student-profile-edit__section" aria-labelledby="edit-badges">
          <h3 id="edit-badges" className="student-profile-edit__title">
            전공역량·특기{" "}
            <span aria-live="polite">
              {badges.length}/{MAX_SPECIALTY_BADGES}
            </span>
          </h3>
          <p
            className={`student-profile-edit__guide${limitReached || badges.length === 0 ? " student-profile-edit__guide--alert" : ""}`}
            role={limitReached ? "alert" : undefined}
          >
            {limitReached
              ? "최대 5개까지 고를 수 있어요"
              : badges.length === 0
                ? "뱃지를 1개 이상 골라 주세요"
                : "해당하는 뱃지를 눌러 골라 주세요 (최대 5개)"}
          </p>
          <div className="student-profile-edit__groups">
            {SPECIALTY_BADGES.map(({ field, badges: fieldBadges }) => (
              <div key={field} className="student-profile-edit__group">
                <h4 className="student-profile-edit__group-title">{field}</h4>
                <div className="student-profile-edit__chips">
                  {fieldBadges.map((badge) => (
                    <Chip
                      key={badge}
                      variant="outlined"
                      label={badge}
                      selected={badges.includes(badge)}
                      onClick={() => toggleBadge(badge)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section
          ref={certificatesRef}
          className="student-profile-edit__section"
          aria-labelledby="edit-certificates"
        >
          <div>
            <h3 id="edit-certificates" className="student-profile-edit__title">
              보유 자격증
            </h3>
            <p className="student-profile-edit__guide">자격증 이름과 취득한 연월을 적어 주세요</p>
          </div>
          {certificates.map((certificate, i) => (
            <div key={i} className="student-profile-edit__cert">
              <div className="student-profile-edit__cert-row">
                <input
                  className="student-profile-edit__cert-input"
                  placeholder="자격증명"
                  aria-label={`자격증 ${i + 1} 이름`}
                  value={certificate.name}
                  onChange={(e) => editCertificate(i, { name: e.target.value })}
                />
                <input
                  className={`student-profile-edit__cert-input student-profile-edit__cert-input--date${dateInvalid(certificate) ? " student-profile-edit__cert-input--invalid" : ""}`}
                  placeholder="2024.02"
                  inputMode="decimal"
                  aria-label={`자격증 ${i + 1} 취득 연월`}
                  aria-invalid={dateInvalid(certificate) || undefined}
                  value={certificate.acquired}
                  onChange={(e) => editCertificate(i, { acquired: e.target.value })}
                />
              </div>
              {dateInvalid(certificate) && (
                <p className="student-profile-edit__cert-error">취득 연월을 2024.02처럼 적어 주세요</p>
              )}
            </div>
          ))}
          <button
            type="button"
            className="student-profile-edit__cert-add"
            onClick={() => setCertificates((rows) => [...rows, { name: "", acquired: "" }])}
          >
            + 자격증 추가
          </button>
        </section>

        <label className="student-profile-edit__field">
          <span className="student-profile-edit__title">외부 포트폴리오</span>
          <TextField
            type="url"
            inputMode="url"
            placeholder="링크를 입력해 주세요"
            value={portfolioUrl}
            onChange={(e) => setPortfolioUrl(e.target.value)}
          />
        </label>
      </div>
    </SubScreen>
  );
}

export default StudentProfileEditPage;
