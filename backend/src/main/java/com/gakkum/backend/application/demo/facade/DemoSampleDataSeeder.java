package com.gakkum.backend.application.demo.facade;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.category.dto.BusinessCategoryResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.chat.service.ChatService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;

/**
 * 새 데모 세션의 예시 데이터. 방문자 사장님·학생이 홈·내 활동·탐색·채팅·결제 내역의 상태를 처음부터 모두 볼 수 있게 채운다.
 * 학생 지원자·다른 학생의 제안·다른 가게의 의뢰가 필요해서 같은 세션에 로그인할 수 없는 예시 사장님·학생을 둔다.
 * 결제는 카카오페이를 거치지 않고 PaymentApprovalService·ProposalFacade와 같은 순서로 상태만 바꾼다.
 * 지난 일(작성 · 지원 · 결제 · 제출 · 수정 요청 · 완료 · 후기 · 취소)은 결제일과 마감에 맞는 과거 시각으로 둔다.
 */
@Component
@ConditionalOnProperty(name = "demo-login.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSampleDataSeeder {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String UNIVERSITY = "광운대학교";
    // 제출물 원본 파일 자리. 받기를 누르면 그림이 열리고 화면에는 주소 끝(크기.png)이 파일 이름으로 보인다
    private static final String SAMPLE_FILE_BASE = "https://placehold.co/";

    private final UserService userService;
    private final OwnerService ownerService;
    private final StudentService studentService;
    private final BusinessCategoryService businessCategoryService;
    private final SpecialtyService specialtyService;
    private final JobService jobService;
    private final ProposalService proposalService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final ChatRoomService chatRoomService;
    private final ChatService chatService;
    private final Clock clock;
    // 지난 일의 시각을 옮길 때 쓴다 (Session.applyTimeline)
    @PersistenceContext
    private EntityManager entityManager;

    /** 예시 데이터를 받을 방문자 한 쌍의 가게와 학생 프로필. */
    public record Visitor(String demoSessionId, Owner store, Student student) {
    }

    /** 예시 데이터를 다 만든 뒤 바꿀 시각 한 칸. at 은 칼럼 타입에 맞춘 값이다 */
    private record TimelineUpdate(String sql, Object at, Long id) {
    }

    @Transactional
    public void seed(Visitor visitor) {
        Session session = new Session(visitor);
        session.seed();
        session.applyTimeline();
    }

    /** 세션 하나를 채우는 동안의 기준 날짜와 기준 데이터. */
    private final class Session {

        private final String demoSessionId;
        private final Owner myStore;
        private final Student me;
        private final Instant now;
        private final LocalDate today;
        private final Map<String, Long> specialtyIds;
        private final Map<String, Long> categoryIds;
        private final List<TimelineUpdate> timeline = new ArrayList<>();

        private Session(Visitor visitor) {
            this.demoSessionId = visitor.demoSessionId();
            this.myStore = visitor.store();
            this.me = visitor.student();
            this.now = clock.instant();
            this.today = LocalDate.ofInstant(now, KOREA);
            this.specialtyIds = specialtyService.getSpecialtyIdsByName();
            this.categoryIds = businessCategoryService.getBusinessCategories().stream().collect(Collectors.toMap(
                    BusinessCategoryResponse::getName, BusinessCategoryResponse::getId, (first, second) -> first));
        }

        private void seed() {
            Student kim = student(1, 24, "김광운", "시각디자인학과",
                    "메뉴판·포스터처럼 가게에서 바로 쓰는 디자인을 좋아해요.", "메뉴판·가격표 디자인", "전단지·포스터 디자인");
            Student park = student(2, 23, "박지은", "시각디자인학과",
                    "쿠폰·스티커처럼 손님 손에 남는 디자인을 만들어요.", "쿠폰·스티커·명함 디자인");
            Student lee = student(3, 22, "이은서", "경영학부",
                    "리뷰와 매출을 정리해 다음에 할 일을 찾아 드려요.", "리뷰 분석", "홍보·이벤트 기획");
            Student nuri = student(4, 25, "박누리", "미디어커뮤니케이션학부",
                    "SNS 게시물과 짧은 영상을 만들어요.", "SNS 게시물", "영상 제작 및 편집");
            Student choi = student(5, 21, "최하늘", "영어산업학과",
                    "메뉴판 번역과 안내문 쓰기를 도와 드려요.", "영어 번역", "소개·공지 글쓰기");

            Owner dino = store(1, "정민호", "공룡카페", "카페", "서울 노원구 광운로 12길 5",
                    "공룡 피규어가 가득한 동네 카페예요.");
            Owner kwCafe = store(2, "한지수", "광운카페", "카페", "서울 노원구 광운로 21",
                    "광운대 정문 앞 스터디 카페예요.");
            Owner banjeom = store(3, "오승철", "월계반점", "음식점", "서울 노원구 월계로 45길 8",
                    "30년 된 동네 중국집이에요.");
            Owner chicken = store(4, "배정훈", "치킨플러스", "음식점", "서울 노원구 석계로 3",
                    "배달이 많은 치킨집이에요.");

            seedMyStoreJobs(kim, park, lee, nuri, choi);
            seedMyStoreProposals(kim, park, lee, nuri, choi);
            seedOtherStores(dino, kwCafe, banjeom, chicken, kim, park, lee, nuri, choi);
        }

        /** 방문자 가게의 의뢰: 모집 중 · 지원자 있음 · 작업 중 · 초안 도착 · 수정 중 · 수정안 도착 · 완료 · 취소. */
        private void seedMyStoreJobs(Student kim, Student park, Student lee, Student nuri, Student choi) {
            openJob(myStore, at(1, 10), "봄 신메뉴 전단지",
                    "봄 신메뉴 3가지를 알리는 A4 전단지를 만들어 주세요. 메뉴 사진은 드릴게요.",
                    40_000L, 6, 12, 1, "전단지·포스터 디자인");

            Job instagram = openJob(myStore, at(2, 11), "신메뉴 인스타그램 홍보 게시물",
                    "신메뉴 출시에 맞춰 인스타그램에 올릴 게시물 3장을 만들어 주세요. 매장 사진은 드릴게요.",
                    50_000L, 5, 10, 2, "SNS 게시물");
            apply(instagram, nuri, at(1, 14), "릴스 감성으로 신메뉴 3장을 만들어 드릴게요",
                    "1일차 사진 고르기, 2~3일차 시안 2가지, 4일차 고친 최종본을 드려요.", "인스타그램용 1080×1350 이미지 3장");
            apply(instagram, kim, at(1, 19), "메뉴 사진이 돋보이는 깔끔한 카드뉴스로 만들게요",
                    "첫 장은 가게 소개, 나머지 두 장은 메뉴별로 나눠 만들어요.", "PNG 3장과 편집할 수 있는 원본 파일");

            Job menu = openJob(myStore, at(5, 10), "메뉴판 디자인 변경",
                    "오래된 메뉴판을 새로 디자인하고 싶어요. A4 한 장이고 인쇄용 파일이 필요해요.",
                    80_000L, 4, 11, 1, "메뉴판·가격표 디자인");
            match(myStore, menu, apply(menu, kim, at(4, 13), "인기 메뉴가 먼저 보이는 메뉴판으로 바꿔 드릴게요",
                    "가격대별로 묶고 대표 메뉴 사진을 크게 넣어요.", "인쇄용 PDF와 원본 파일"), at(2, 15));

            Job coupon = openJob(myStore, at(9, 10), "단골 쿠폰·도장카드 디자인",
                    "도장 10개를 모으면 음료 한 잔을 드리는 쿠폰을 만들고 싶어요.",
                    45_000L, 1, 8, 2, "쿠폰·스티커·명함 디자인");
            match(myStore, coupon, apply(coupon, park, at(8, 12), "귀여운 캐릭터가 들어간 도장카드를 만들어 드려요",
                    "시안 2가지를 먼저 보여 드리고 고르신 쪽으로 다듬어요.", "명함 크기 인쇄용 PDF"), at(6, 14));
            submitDraft(coupon, park, at(1, 20), "도장카드 시안 2가지예요. 마음에 드는 쪽을 알려 주세요.",
                    "1080x1080.png?text=Coupon+A", "1080x1350.png?text=Coupon+B");

            Job reels = openJob(myStore, at(8, 11), "가게 소개 릴스 영상 편집",
                    "가게 분위기를 보여 주는 30초 릴스 영상을 편집해 주세요. 찍어 둔 영상을 드릴게요.",
                    70_000L, -1, 6, 2, "영상 제작 및 편집");
            match(myStore, reels, apply(reels, me, at(7, 13), "음악에 맞춰 장면이 바뀌는 30초 릴스를 만들게요",
                    "1~2일차 장면 고르기, 3일차 자막 넣기, 4일차 초안을 드려요.", "세로 1080×1920 MP4"), at(5, 16));
            JobSubmission reelsDraft = submitDraft(reels, me, at(2, 19), "초안 영상이에요. 음악은 바꿀 수 있어요.",
                    "1080x1920.png?text=Reels+Draft");
            requestRevision(reels, reelsDraft, at(1, 11), "자막을 조금 키우고 마지막 장면에 가게 위치를 넣어 주세요.");
            chat(reels, me, at(2, 20), "초안 올렸어요! 확인 부탁드려요.",
                    at(1, 12), "수정 요청 남겼어요. 자막만 조금 키워 주시면 될 것 같아요.");

            Job reviews = openJob(myStore, at(12, 10), "배달앱 리뷰 분석 리포트",
                    "배달앱 리뷰 3개월치를 모아 손님이 아쉬워한 점을 정리해 주세요.",
                    60_000L, -3, 4, 1, "리뷰 분석");
            match(myStore, reviews, apply(reviews, lee, at(11, 15), "리뷰를 주제별로 묶어 바로 고칠 점을 뽑아 드릴게요",
                    "리뷰 수집, 주제 분류, 개선 제안 순서로 정리해요.", "PDF 리포트 1부"), at(9, 14));
            JobSubmission reviewsDraft = submitDraft(reviews, lee, at(4, 21), "리뷰 리포트 초안이에요.",
                    "1240x1754.png?text=Report+Draft");
            requestRevision(reviews, reviewsDraft, at(3, 10), "포장 관련 의견을 따로 한 장으로 모아 주세요.");
            submitRevision(reviews, lee, at(1, 22), "포장 의견을 따로 모은 수정안이에요.", "1240x1754.png?text=Report+v2");

            Job posts = openJob(myStore, at(48, 10), "인스타 게시물 5개 제작",
                    "가게 계정에 올릴 게시물 5개를 만들어 주세요.", 75_000L, -40, -33, 1, "SNS 게시물");
            match(myStore, posts, apply(posts, me, at(47, 14), "가게 사진으로 통일감 있는 게시물 5개를 만들게요",
                    "주제 정하기, 사진 보정, 문구 쓰기 순서로 진행해요.", "PNG 5장"), at(45, 15));
            complete(posts, submitDraft(posts, me, at(41, 20), "게시물 5개예요.",
                    "1080x1080.png?text=Post+1", "1080x1350.png?text=Post+2"), at(38, 11));
            review(posts, me, at(38, 12), 5, "사진 보정이 깔끔하고 일정도 딱 맞춰 주셨어요.",
                    ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY,
                    ReviewPositivePoint.FAST_COMMUNICATION);

            Job signboard = openJob(myStore, at(33, 10), "가게 앞 입간판 시안",
                    "가게 앞에 세울 입간판 시안을 만들어 주세요.", 55_000L, -25, -18, 1, "간판·현수막 시안");
            match(myStore, signboard, apply(signboard, kim, at(32, 13), "멀리서도 보이는 입간판으로 만들게요",
                    "글씨 크기를 먼저 정하고 색 조합 2가지를 보여 드려요.", "인쇄용 PDF"), at(30, 15));
            JobSubmission signDraft = submitDraft(signboard, kim, at(26, 19), "입간판 시안이에요.",
                    "1200x1800.png?text=Sign+Draft");
            requestRevision(signboard, signDraft, at(24, 10), "가게 이름을 조금 더 크게 해 주세요.");
            complete(signboard, submitRevision(signboard, kim, at(20, 21), "가게 이름을 키웠어요.",
                    "1200x1800.png?text=Sign+Final"), at(19, 11));

            Job sticker = openJob(myStore, at(13, 10), "포장 스티커 디자인",
                    "포장 용기에 붙일 원형 스티커를 만들어 주세요.", 35_000L, 3, 9, 1, "쿠폰·스티커·명함 디자인");
            match(myStore, sticker, apply(sticker, choi, at(12, 14), "가게 로고를 살린 원형 스티커를 만들게요",
                    "로고 정리 후 시안 2가지를 드려요.", "인쇄용 PDF"), at(10, 16));
            cancel(sticker, at(2, 10), "가게 사정으로 디자인을 잠시 미루게 됐어요.", "그동안 고민해 주셔서 감사해요.");
        }

        /** 방문자 가게가 받은 제안: 결정 대기 · 결제 완료(학생 시작 전) · 작업 중 · 학생 거절. */
        private void seedMyStoreProposals(Student kim, Student park, Student lee, Student nuri, Student choi) {
            Proposal discount = propose(me, myStore, at(2, 18), "시험 기간 학생 할인 이벤트",
                    "시험 기간에는 학생 손님이 줄어들어요.",
                    "학생증을 보여 주면 음료를 할인해 주는 이벤트를 기획하고 홍보물까지 만들어 드려요.",
                    "1일차 이벤트 조건 정하기, 2~3일차 홍보물 만들기", 30_000L, 3, 7, "홍보·이벤트 기획");
            like(discount, lee, nuri, choi);

            Proposal translation = propose(choi, myStore, at(1, 15), "영어 메뉴판 번역",
                    "외국인 손님이 메뉴를 고르기 어려워해요.",
                    "메뉴 이름과 설명을 영어로 옮기고 매운 정도를 그림으로 표시해 드려요.",
                    "메뉴 정리, 번역, 검수 순서로 진행해요.", 40_000L, 4, 8, "영어 번역");
            like(translation, kim);

            Proposal lunchMenu = propose(me, myStore, at(4, 17), "점심 세트 메뉴판 정리",
                    "점심 메뉴가 많아 손님이 고르는 데 오래 걸려요.",
                    "점심 세트 3가지를 한눈에 보이게 정리한 메뉴판을 만들어 드려요.",
                    "세트 구성 정리, 시안, 최종본 순서로 진행해요.", 45_000L, 3, 7, "메뉴판·가격표 디자인");
            pay(lunchMenu, myStore, 45_000L, 1, "점심 손님이 많아서 기대돼요!", at(1, 13));

            Proposal intro = propose(me, myStore, at(6, 16), "가게 소개글 다시 쓰기",
                    "지도 앱의 가게 소개글이 오래돼서 지금 메뉴와 맞지 않아요.",
                    "지금 메뉴와 분위기가 드러나는 소개글을 새로 써 드려요.",
                    "가게 인터뷰, 초안, 다듬기 순서로 진행해요.", 30_000L, 4, 8, "소개·공지 글쓰기");
            start(intro, pay(intro, myStore, 30_000L, 1, "편하게 써 주세요.", at(3, 14)), at(2, 10));

            Proposal groupOrder = propose(park, myStore, at(11, 15), "단체 주문 안내문 디자인",
                    "단체 주문 방법을 묻는 전화가 많아요.",
                    "단체 주문 방법과 할인을 한 장에 정리한 안내문을 만들어 드려요.",
                    "내용 정리, 시안, 최종본 순서로 진행해요.", 35_000L, 3, 7, "전단지·포스터 디자인");
            decline(groupOrder, pay(groupOrder, myStore, 35_000L, 1, null, at(8, 13)), at(7, 10));
        }

        /** 다른 가게: 방문자 학생의 작업·지원·선택되지 않은 지원, 공감할 다른 학생의 제안, 탐색에 보일 의뢰. */
        private void seedOtherStores(Owner dino, Owner kwCafe, Owner banjeom, Owner chicken,
                                     Student kim, Student park, Student lee, Student nuri, Student choi) {
            Job stickers = openJob(dino, at(9, 11), "쿠폰·스티커 디자인",
                    "공룡 캐릭터가 들어간 쿠폰과 포장 스티커를 만들어 주세요.",
                    50_000L, 2, 9, 2, "쿠폰·스티커·명함 디자인");
            match(dino, stickers, apply(stickers, me, at(8, 15), "공룡 캐릭터를 살린 쿠폰과 스티커를 만들게요",
                    "캐릭터 정리, 쿠폰 시안, 스티커 시안 순서로 진행해요.", "인쇄용 PDF 2개"), at(5, 13));
            submitDraft(stickers, me, at(1, 16), "쿠폰과 스티커 초안이에요.",
                    "1080x1080.png?text=Dino+Coupon", "1080x1350.png?text=Dino+Sticker");
            chat(stickers, me, at(1, 17), "초안 올렸어요. 확인 부탁드려요!", null, null);

            Job menuTranslation = openJob(kwCafe, at(3, 11), "영어·중국어 메뉴판 번역",
                    "외국인 학생 손님을 위해 메뉴판을 영어와 중국어로 옮겨 주세요.",
                    60_000L, 5, 12, 1, "영어 번역", "중국어 번역");
            apply(menuTranslation, me, at(2, 13), "영어·중국어 메뉴판을 한 장에 정리해 드릴게요",
                    "메뉴 정리, 번역, 원어민 검수 순서로 진행해요.", "인쇄용 PDF와 원본 파일");
            apply(menuTranslation, choi, at(1, 10), "자연스러운 영어 메뉴 이름으로 옮겨 드려요",
                    "메뉴 설명을 짧게 다듬어 번역해요.", "PDF 1부");

            Job banner = openJob(banjeom, at(6, 10), "배달 리뷰 이벤트 배너",
                    "배달앱 리뷰 이벤트를 알리는 배너를 만들어 주세요.", 45_000L, 3, 9, 1, "전단지·포스터 디자인");
            apply(banner, me, at(5, 14), "눈에 띄는 리뷰 이벤트 배너를 만들게요",
                    "문구 정하기, 시안 2가지, 최종본 순서로 진행해요.", "배달앱 규격 이미지 2장");
            match(banjeom, banner, apply(banner, nuri, at(5, 18), "배달앱 화면에 맞춘 배너를 만들어 드려요",
                    "앱별 규격을 맞춰 두 가지 크기로 드려요.", "PNG 2장"), at(2, 11));

            openJob(chicken, at(1, 9), "전화 대신 받는 예약서 만들기",
                    "전화 주문이 많아 바빠요. 손님이 직접 쓰는 온라인 예약서를 만들어 주세요.",
                    90_000L, 7, 14, 1, "온라인 예약·주문서");

            Job poster = openJob(dino, at(29, 10), "여름 음료 포스터", "여름 신메뉴 음료 포스터를 만들어 주세요.",
                    40_000L, -22, -16, 1, "전단지·포스터 디자인");
            match(dino, poster, apply(poster, choi, at(28, 13), "시원한 느낌의 음료 포스터를 만들게요",
                    "색 정하기, 시안, 최종본 순서로 진행해요.", "A3 인쇄용 PDF"), at(26, 15));
            complete(poster, submitDraft(poster, choi, at(23, 19), "음료 포스터예요.", "1191x1684.png?text=Summer+Poster"), at(18, 11));
            review(poster, choi, at(18, 12), 4, "색감이 시원해서 손님들이 많이 물어봤어요.", ReviewPositivePoint.QUALITY_OUTPUT);

            Proposal menuDraft = propose(lee, banjeom, at(5, 20), "인기 메뉴를 강조한 메뉴판 시안",
                    "메뉴가 많아 대표 메뉴가 눈에 띄지 않아요.",
                    "주문이 많은 메뉴 5가지를 위로 올리고 사진을 크게 넣은 메뉴판을 만들어 드려요.",
                    "주문 기록 보기, 시안, 최종본 순서로 진행해요.", 35_000L, 3, 7, "메뉴판·가격표 디자인");
            like(menuDraft, kim, park, nuri, choi);

            Proposal examEvent = propose(nuri, kwCafe, at(4, 19), "시험 기간 광운대생 이벤트 기획",
                    "시험 기간에 자리가 비어 있는 시간이 많아요.",
                    "시험 기간 늦은 시간 할인과 SNS 인증 이벤트를 기획해 드려요.",
                    "이벤트 조건 정하기, 홍보물 만들기, SNS 안내 순서로 진행해요.", 30_000L, 3, 7, "홍보·이벤트 기획");
            like(examEvent, lee, choi);

            Proposal photos = propose(kim, chicken, at(3, 21), "치킨 세트 사진 다시 찍기",
                    "배달앱 메뉴 사진이 어두워서 맛있어 보이지 않아요.",
                    "세트 메뉴 4가지를 밝게 다시 찍고 보정해 드려요.",
                    "촬영 날짜 정하기, 촬영, 보정 순서로 진행해요.", 50_000L, 5, 10, "음식·매장 사진");
            like(photos, park);
        }

        /** @param admissionYear 입학 연도 두 자리. 화면에 「24학번」처럼 보인다 */
        private Student student(int number, int admissionYear, String name, String major, String introduction,
                                String... specialties) {
            User user = userService.createDemoSampleUser(demoSessionId, UserRole.STUDENT, number, name);
            Student student = studentService.createStudentProfile(CreateStudentProfileCommand.of(
                    user.getId(), UNIVERSITY, DemoStudentNumbers.next(studentService, admissionYear), major, null,
                    introduction, null));
            for (Long specialtyId : specialties(specialties)) {
                specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
            }
            return student;
        }

        private Owner store(int number, String ownerName, String storeName, String categoryName, String address,
                            String description) {
            User user = userService.createDemoSampleUser(demoSessionId, UserRole.OWNER, number, ownerName);
            Long categoryId = categoryIds.containsKey(categoryName)
                    ? categoryIds.get(categoryName)
                    : businessCategoryService.getFirstCategoryId();
            return ownerService.createOwnerProfile(CreateOwnerProfileCommand.of(
                    user.getId(), "DEMO-" + demoSessionId + "-" + number, null, ownerName, storeName, categoryId,
                    address, description, null, List.of()), demoSessionId);
        }

        private Job openJob(Owner store, Instant postedAt, String title, String description, long budget,
                            int draftInDays, int finalInDays, int revisionCount, String... specialties) {
            Job job = jobService.createJob(CreateJobCommand.of(store.getId(), specialties(specialties), title,
                    description, budget, today.plusDays(draftInDays), today.plusDays(finalInDays), revisionCount),
                    demoSessionId);
            backdate("jobs", "created_at", job.getId(), postedAt);
            return job;
        }

        private JobApplication apply(Job job, Student student, Instant appliedAt, String summary, String workPlan,
                                     String deliveryMethod) {
            JobApplication application = jobService.createJobApplication(CreateJobApplicationCommand.of(
                    null, job.getId(), summary, workPlan, deliveryMethod), student.getId(), demoSessionId);
            backdate("job_applications", "created_at", application.getId(), appliedAt);
            return application;
        }

        /** 사장님이 지원자를 고르고 결제한 상태. 카카오페이 승인 뒤 PaymentApprovalService가 하는 일과 같다. */
        private void match(Owner store, Job job, JobApplication application, Instant approvedAt) {
            Payment payment = paymentService.preparePayment(PreparePaymentCommand.of(
                    job.getId(), application.getId(), store.getUserId(), job.getBudget()));
            payment.recordKakaoTid(newTid());
            job.match(application.getStudentProfileId());
            application.accept();
            payment.approve(approvedAt);
            chatRoomService.createIfAbsent(job.getId());
            backdateByJob("chat_rooms", "created_at", job.getId(), approvedAt);
        }

        private JobSubmission submitDraft(Job job, Student student, Instant submittedAt, String message,
                                          String... files) {
            JobSubmission submission = jobService.submitDraft(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId());
            backdate("job_submissions", "created_at", submission.getId(), submittedAt);
            return submission;
        }

        private JobSubmission submitRevision(Job job, Student student, Instant submittedAt, String message,
                                             String... files) {
            JobSubmission submission = jobService.submitRevision(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId());
            backdate("job_submissions", "created_at", submission.getId(), submittedAt);
            return submission;
        }

        // 수정 요청 시각은 제출물의 reviewed_at 에 남는다
        private void requestRevision(Job job, JobSubmission submission, Instant requestedAt, String message) {
            jobService.requestRevision(RequestJobSubmissionRevisionCommand.of(
                    null, job.getId(), submission.getId(), message, List.of()), job.getOwnerProfileId());
            backdate("job_submissions", "reviewed_at", submission.getId(), requestedAt);
        }

        private void complete(Job job, JobSubmission submission, Instant completedAt) {
            jobService.completeSubmission(CompleteJobSubmissionCommand.of(
                    job.getId(), submission.getId(), job.getOwnerProfileId()));
            backdate("job_submissions", "reviewed_at", submission.getId(), completedAt);
            backdate("jobs", "completed_at", job.getId(), completedAt);
        }

        private void review(Job job, Student student, Instant reviewedAt, int rating, String content,
                            ReviewPositivePoint... points) {
            reviewService.createReview(CreateReviewCommand.of(null, job.getId(), List.of(points), content, rating),
                    job.getOwnerProfileId(), student.getId());
            backdateByJob("reviews", "created_at", job.getId(), reviewedAt);
        }

        /** 사장님이 작업 중에 취소해 착수 보상을 뺀 금액이 환불된 상태. JobFacade.cancelJob과 같다. */
        private void cancel(Job job, Instant cancelledAt, String reason, String messageToStudent) {
            jobService.cancelJob(CancelJobCommand.of(null, job.getId(), reason, messageToStudent),
                    job.getOwnerProfileId());
            paymentService.refundOnCancel(job.getId());
            backdate("jobs", "completed_at", job.getId(), cancelledAt);
            backdateRefund(job.getId(), cancelledAt);
        }

        /** 학생이 먼저 보낸 메시지를 사장님이 읽었고, 사장님 답장은 학생이 아직 읽지 않은 채팅방. */
        private void chat(Job job, Student student, Instant studentAt, String studentMessage, Instant replyAt,
                          String ownerReply) {
            ChatRoom room = chatRoomService.getOrCreate(job.getId());
            Long studentMessageId = chatService.sendTextMessage(
                    room, student.getUserId(), UUID.randomUUID(), studentMessage).getMessage().getId();
            backdate("chat_messages", "created_at", studentMessageId, studentAt);
            if (ownerReply != null) {
                Owner store = ownerService.getOwnerProfileById(job.getOwnerProfileId());
                chatService.markRead(room, true, studentMessageId);
                Long replyId = chatService.sendTextMessage(
                        room, store.getUserId(), UUID.randomUUID(), ownerReply).getMessage().getId();
                backdate("chat_messages", "created_at", replyId, replyAt);
            }
        }

        private Proposal propose(Student student, Owner store, Instant createdAt, String title, String customerProblem,
                                 String proposedSolution, String workPlan, long proposedFee, int draftDays,
                                 int finalDays, String... specialties) {
            Proposal proposal = proposalService.createProposal(CreateProposalCommand.of(
                    null, store.getId(), specialties(specialties), title, customerProblem, proposedSolution, workPlan,
                    proposedFee, draftDays, finalDays, List.of()), student.getId(), demoSessionId);
            backdate("proposals", "created_at", proposal.getId(), createdAt);
            return proposal;
        }

        private void like(Proposal proposal, Student... likers) {
            for (Student liker : likers) {
                proposalService.likeProposal(proposal.getId(), liker.getId(), demoSessionId);
            }
        }

        /** 사장님이 제안을 받아 결제한 상태(학생 시작 전). PaymentApprovalService.approveProposalPayment와 순서가 같다. */
        private Job pay(Proposal proposal, Owner store, long amount, int revisionCount, String messageToStudent,
                        Instant approvedAt) {
            Payment payment = paymentService.prepareProposalPayment(
                    proposal.getId(), store.getUserId(), amount, revisionCount, messageToStudent);
            payment.recordKakaoTid(newTid());
            Job job = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                    proposal, proposalService.getSpecialtyIds(proposal.getId()), payment.getAmount(),
                    LocalDate.ofInstant(approvedAt, KOREA), payment.getRevisionCount(), payment.getMessageToStudent()));
            proposal.awaitStart();
            payment.approve(approvedAt);
            payment.linkJob(job.getId());
            backdate("jobs", "created_at", job.getId(), approvedAt);
            return job;
        }

        /** 학생이 의뢰서 조건을 보고 작업을 시작한 상태. ProposalFacade.startProposalJob과 같다. */
        private void start(Proposal proposal, Job job, Instant startedAt) {
            jobService.startJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.accept();
            chatRoomService.getOrCreate(job.getId());
            backdate("jobs", "started_at", job.getId(), startedAt);
            backdateByJob("chat_rooms", "created_at", job.getId(), startedAt);
        }

        /** 학생이 의뢰서를 거절해 전액 환불된 상태. ProposalFacade.declineProposalJob과 같다. */
        private void decline(Proposal proposal, Job job, Instant declinedAt) {
            Owner store = ownerService.getOwnerProfileById(proposal.getOwnerProfileId());
            jobService.declineJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.rejectByStudent(LocalDateTime.ofInstant(declinedAt, ZoneOffset.UTC));
            paymentService.refundOnDecline(job.getId(), proposal.getId(), store.getUserId());
            backdate("jobs", "completed_at", job.getId(), declinedAt);
            backdateRefund(job.getId(), declinedAt);
        }

        private List<Long> specialties(String... names) {
            return Arrays.stream(names).filter(specialtyIds::containsKey).map(specialtyIds::get).toList();
        }

        private List<String> sampleFiles(String... files) {
            return Arrays.stream(files).map(file -> SAMPLE_FILE_BASE + file).toList();
        }

        /** 오늘에서 days 일 전 hour 시(한국 시간). 지난 일은 모두 이 시각에 일어난 것으로 둔다 (days 는 1 이상) */
        private Instant at(int days, int hour) {
            return today.minusDays(days).atTime(hour, 0).atZone(KOREA).toInstant();
        }

        /*
         * 지난 일의 시각을 적어 둔다. 서비스는 지금 시각으로 저장하고 생성 시각 칼럼은 엔티티로 바꿀 수 없어서
         * (updatable = false) 예시 데이터를 다 만든 뒤 applyTimeline 에서 한꺼번에 고친다.
         * TIMESTAMP 칼럼은 서비스의 now() 처럼 서버 기본 시간대의 시각으로 저장한다.
         */
        private void backdate(String table, String column, Long id, Instant at) {
            timeline.add(new TimelineUpdate("update " + table + " set " + column + " = :at where id = :id",
                    LocalDateTime.ofInstant(at, ZoneId.systemDefault()), id));
        }

        private void backdateByJob(String table, String column, Long jobId, Instant at) {
            timeline.add(new TimelineUpdate("update " + table + " set " + column + " = :at where job_id = :id",
                    LocalDateTime.ofInstant(at, ZoneId.systemDefault()), jobId));
        }

        // 환불 시각은 TIMESTAMP WITH TIME ZONE 칼럼이다
        private void backdateRefund(Long jobId, Instant at) {
            timeline.add(new TimelineUpdate(
                    "update payments set refunded_at = :at where job_id = :id and refunded_at is not null",
                    at.atOffset(ZoneOffset.UTC), jobId));
        }

        /**
         * 적어 둔 시각을 DB 에 옮긴다. 영속성 컨텍스트의 엔티티는 옮기기 전 시각을 들고 있어서,
         * 같은 트랜잭션의 다음 조회가 DB 값을 읽도록 비운다.
         */
        private void applyTimeline() {
            entityManager.flush();
            for (TimelineUpdate update : timeline) {
                entityManager.createNativeQuery(update.sql())
                        .setParameter("at", update.at())
                        .setParameter("id", update.id())
                        .executeUpdate();
            }
            entityManager.clear();
        }

        // kakao_tid는 고유한 20자 이하 값이어야 한다
        private String newTid() {
            return "DEMO" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
