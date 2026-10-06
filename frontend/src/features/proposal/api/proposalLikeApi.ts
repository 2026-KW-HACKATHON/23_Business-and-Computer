import { apiData } from "../../../api/client";

/** POST · DELETE /proposals/{id}/likes 의 답. 공감 수는 이번 변경이 반영된 값 */
export interface ProposalLikeResponse {
  proposalId: number;
  likeCount: number;
  likedByMe: boolean;
}

/** 공감을 켜거나(POST) 끈다(DELETE). 같은 요청을 다시 보내도 성공한다. 학생만 쓸 수 있다 */
export async function sendProposalLike(
  proposalId: number,
  like: boolean,
): Promise<ProposalLikeResponse> {
  const data = await apiData<ProposalLikeResponse | undefined>(`/proposals/${proposalId}/likes`, {
    method: like ? "POST" : "DELETE",
  });
  if (!data) throw new Error("Proposal like response has no data");
  return data;
}
