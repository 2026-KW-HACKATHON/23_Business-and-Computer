import { useParams } from "react-router-dom";
import { LoadNotice, SubScreen } from "../components";
import {
  ChatWorkHistory,
  chatWorkBadge,
  chatWorkEntries,
  chatWorkEntriesFromSubmissions,
  chatWorkEntryLabel,
  chatWorkFlowIndex,
  chatWorkStageOf,
  useChatRooms,
} from "../features/chat";
import {
  STUDENT_PATHS,
  StudentMissing,
  flowSteps,
  studentWorkDocPath,
  studentWorkDocSub,
  useSubmissionHistory,
} from "../features/student";
import { useBack } from "../hooks/useBack";
import { formatWon } from "../lib/money";

/** 숫자로만 된 작업 id. 아니면 undefined */
const jobIdOf = (workId: string) => (/^[1-9][0-9]*$/.test(workId) ? Number(workId) : undefined);

/**
 * 피그마 「작업 이력 (학생)」 (ADR 0045). 채팅 작업 카드 「이력 상세보기 ›」. 그 작업의 채팅방(GET /me/chat-rooms)과
 * 서류 이력(GET /jobs/{id}/submissions)으로 쌓인 서류를 생긴 순서대로 보인다. 줄마다 그 서류 화면으로 가고,
 * 지난 초안 · 수정 요청 · 수정안도 열린다. 끝난 작업에는 받은 후기 줄이 붙는다. 이력을 불러오기 전에는 지금
 * 단계로 어림한 줄을 보인다
 */
function StudentWorkHistoryPage() {
  const { workId = "" } = useParams();
  const jobId = jobIdOf(workId);
  const back = useBack(STUDENT_PATHS.chats);
  const { load, reload } = useChatRooms();
  const { load: submissionsLoad } = useSubmissionHistory(jobId ?? 0);

  const room = load.status === "loaded" ? load.rooms.find((r) => r.jobId === jobId) : undefined;
  if (jobId === undefined || (load.status === "loaded" && !room)) {
    return <StudentMissing title="작업 이력" onBack={back} />;
  }
  if (!room) {
    return (
      <SubScreen title="작업 이력" onBack={back}>
        <LoadNotice
          layout="page"
          status={load.status === "error" ? "error" : "loading"}
          loadingText="작업 이력을 불러오는 중이에요"
          errorText="작업 이력을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const stage = chatWorkStageOf(room);
  const proposalId = room.proposalId ?? undefined;
  const kind = proposalId !== undefined ? "proposal" : "request";
  const entries =
    submissionsLoad.status === "loaded"
      ? chatWorkEntriesFromSubmissions(stage, submissionsLoad.submissions, true)
      : chatWorkEntries(stage, room.revisionNumber ?? undefined, true);
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인 중" : "작업 중";

  return (
    <SubScreen title="작업 이력" onBack={back}>
      <ChatWorkHistory
        role="student"
        kind={kind}
        title={room.jobTitle}
        badge={chatWorkBadge(stage)}
        meta={`${room.counterpartName} · 작업비 ${formatWon(room.budget)} · 수정 ${room.revisionCount}회`}
        steps={
          flowIndex === undefined
            ? undefined
            : flowSteps(kind === "proposal" ? "제안" : "의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)
        }
        rows={entries.map((entry) => ({
          label: chatWorkEntryLabel(entry, kind),
          sub: studentWorkDocSub(entry.doc, kind),
          to: studentWorkDocPath(entry, jobId, stage, proposalId),
        }))}
        note={submissionsLoad.status === "error" ? "지난 서류를 불러오지 못했어요" : undefined}
      />
    </SubScreen>
  );
}

export default StudentWorkHistoryPage;
