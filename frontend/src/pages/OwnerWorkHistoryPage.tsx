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
  OWNER_PATHS,
  OwnerMissing,
  flowSteps,
  isOwnerWorkReviewed,
  ownerWorkDocPath,
  ownerWorkDocSub,
  parsePositiveId,
  useOwnerClosedJobs,
  useJobSubmissions,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";

/**
 * 피그마 「작업 이력 (사장님)」 (ADR 0045). 채팅 작업 카드 「이력 상세보기 ›」. 그 작업의 채팅방(GET /me/chat-rooms)과
 * 서류 이력(GET /jobs/{id}/submissions)으로 쌓인 서류를 생긴 순서대로 보인다. 줄마다 그 서류 화면으로 가고,
 * 지난 초안 · 수정 요청 · 수정안도 열린다. 이력을 불러오기 전에는 지금 단계로 어림한 줄을 보인다.
 * 후기를 남겼으면(끝난 목록의 reviewed · 이 화면을 연 동안 남긴 후기) 끝에 「후기」 줄
 */
function OwnerWorkHistoryPage() {
  const { workId } = useParams();
  const jobId = parsePositiveId(workId);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useChatRooms();
  const { load: submissionsLoad } = useJobSubmissions(jobId);
  const { load: closedLoad } = useOwnerClosedJobs();

  const room = load.status === "loaded" ? load.rooms.find((r) => r.jobId === jobId) : undefined;
  if (jobId === undefined || (load.status === "loaded" && !room)) {
    return <OwnerMissing title="작업 이력" onBack={back} />;
  }
  if (!room) {
    return (
      <SubScreen title="작업 이력" onBack={back}>
        <LoadNotice
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
  const reviewed =
    isOwnerWorkReviewed(String(jobId)) ||
    (closedLoad.status === "loaded" && closedLoad.jobs.some((job) => job.jobId === jobId && job.reviewed));
  const entries =
    submissionsLoad.status === "loaded"
      ? chatWorkEntriesFromSubmissions(stage, submissionsLoad.data, reviewed)
      : chatWorkEntries(stage, room.revisionNumber ?? undefined, reviewed);
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인해 주세요" : "작업 중";

  return (
    <SubScreen title="작업 이력" onBack={back}>
      <ChatWorkHistory
        role="owner"
        kind={kind}
        title={room.jobTitle}
        badge={chatWorkBadge(stage)}
        meta={`${studentTitle(room.counterpartName)}\n작업비 ${formatWon(room.budget)} · 수정 ${room.revisionCount}회`}
        steps={
          flowIndex === undefined
            ? undefined
            : flowSteps(kind === "proposal" ? "제안" : "의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)
        }
        rows={entries.map((entry) => ({
          label: chatWorkEntryLabel(entry, kind),
          sub: ownerWorkDocSub(entry.doc, kind, entry.past ? undefined : stage),
          to: ownerWorkDocPath(entry, jobId, stage, proposalId),
        }))}
        note={submissionsLoad.status === "error" ? "지난 서류를 불러오지 못했어요" : undefined}
      />
    </SubScreen>
  );
}

export default OwnerWorkHistoryPage;
