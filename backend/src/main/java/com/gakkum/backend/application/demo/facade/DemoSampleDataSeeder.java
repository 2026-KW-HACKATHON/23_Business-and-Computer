package com.gakkum.backend.application.demo.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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

import lombok.RequiredArgsConstructor;

/**
 * 새 데모 세션의 예시 데이터. 방문자 사장님·학생이 홈·내 활동·탐색·채팅·결제 내역의 상태를 처음부터 모두 볼 수 있게 채운다.
 * 학생 지원자·다른 학생의 제안·다른 가게의 의뢰가 필요해서 같은 세션에 로그인할 수 없는 예시 사장님·학생을 둔다.
 * 결제는 카카오페이를 거치지 않고 PaymentApprovalService·ProposalFacade와 같은 순서로 상태만 바꾼다.
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

    /** 예시 데이터를 받을 방문자 한 쌍의 가게와 학생 프로필. */
    public record Visitor(String demoSessionId, Owner store, Student student) {
    }

    @Transactional
    public void seed(Visitor visitor) {
        new Session(visitor).seed();
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
            Student kim = student(1, "김광운", "시각디자인학과",
                    "메뉴판·포스터처럼 가게에서 바로 쓰는 디자인을 좋아해요.", "메뉴판·가격표 디자인", "전단지·포스터 디자인");
            Student park = student(2, "박지은", "시각디자인학과",
                    "쿠폰·스티커처럼 손님 손에 남는 디자인을 만들어요.", "쿠폰·스티커·명함 디자인");
            Student lee = student(3, "이은서", "경영학부",
                    "리뷰와 매출을 정리해 다음에 할 일을 찾아 드려요.", "리뷰 분석", "홍보·이벤트 기획");
            Student nuri = student(4, "박누리", "미디어커뮤니케이션학부",
                    "SNS 게시물과 짧은 영상을 만들어요.", "SNS 게시물", "영상 제작 및 편집");
            Student choi = student(5, "최하늘", "영어산업학과",
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
            openJob(myStore, "봄 신메뉴 전단지",
                    "봄 신메뉴 3가지를 알리는 A4 전단지를 만들어 주세요. 메뉴 사진은 드릴게요.",
                    40_000L, 6, 12, 1, "전단지·포스터 디자인");

            Job instagram = openJob(myStore, "신메뉴 인스타그램 홍보 게시물",
                    "신메뉴 출시에 맞춰 인스타그램에 올릴 게시물 3장을 만들어 주세요. 매장 사진은 드릴게요.",
                    50_000L, 5, 10, 2, "SNS 게시물");
            apply(instagram, nuri, "릴스 감성으로 신메뉴 3장을 만들어 드릴게요",
                    "1일차 사진 고르기, 2~3일차 시안 2가지, 4일차 고친 최종본을 드려요.", "인스타그램용 1080×1350 이미지 3장");
            apply(instagram, kim, "메뉴 사진이 돋보이는 깔끔한 카드뉴스로 만들게요",
                    "첫 장은 가게 소개, 나머지 두 장은 메뉴별로 나눠 만들어요.", "PNG 3장과 편집할 수 있는 원본 파일");

            Job menu = openJob(myStore, "메뉴판 디자인 변경",
                    "오래된 메뉴판을 새로 디자인하고 싶어요. A4 한 장이고 인쇄용 파일이 필요해요.",
                    80_000L, 4, 11, 1, "메뉴판·가격표 디자인");
            match(myStore, menu, apply(menu, kim, "인기 메뉴가 먼저 보이는 메뉴판으로 바꿔 드릴게요",
                    "가격대별로 묶고 대표 메뉴 사진을 크게 넣어요.", "인쇄용 PDF와 원본 파일"), daysAgo(2));

            Job coupon = openJob(myStore, "단골 쿠폰·도장카드 디자인",
                    "도장 10개를 모으면 음료 한 잔을 드리는 쿠폰을 만들고 싶어요.",
                    45_000L, 1, 8, 2, "쿠폰·스티커·명함 디자인");
            match(myStore, coupon, apply(coupon, park, "귀여운 캐릭터가 들어간 도장카드를 만들어 드려요",
                    "시안 2가지를 먼저 보여 드리고 고르신 쪽으로 다듬어요.", "명함 크기 인쇄용 PDF"), daysAgo(6));
            submitDraft(coupon, park, "도장카드 시안 2가지예요. 마음에 드는 쪽을 알려 주세요.",
                    "1080x1080.png?text=Coupon+A", "1080x1350.png?text=Coupon+B");

            Job reels = openJob(myStore, "가게 소개 릴스 영상 편집",
                    "가게 분위기를 보여 주는 30초 릴스 영상을 편집해 주세요. 찍어 둔 영상을 드릴게요.",
                    70_000L, -1, 6, 2, "영상 제작 및 편집");
            match(myStore, reels, apply(reels, me, "음악에 맞춰 장면이 바뀌는 30초 릴스를 만들게요",
                    "1~2일차 장면 고르기, 3일차 자막 넣기, 4일차 초안을 드려요.", "세로 1080×1920 MP4"), daysAgo(5));
            JobSubmission reelsDraft = submitDraft(reels, me, "초안 영상이에요. 음악은 바꿀 수 있어요.",
                    "1080x1920.png?text=Reels+Draft");
            requestRevision(reels, reelsDraft, "자막을 조금 키우고 마지막 장면에 가게 위치를 넣어 주세요.");
            chat(reels, me, "초안 올렸어요! 확인 부탁드려요.", "수정 요청 남겼어요. 자막만 조금 키워 주시면 될 것 같아요.");

            Job reviews = openJob(myStore, "배달앱 리뷰 분석 리포트",
                    "배달앱 리뷰 3개월치를 모아 손님이 아쉬워한 점을 정리해 주세요.",
                    60_000L, -3, 4, 1, "리뷰 분석");
            match(myStore, reviews, apply(reviews, lee, "리뷰를 주제별로 묶어 바로 고칠 점을 뽑아 드릴게요",
                    "리뷰 수집, 주제 분류, 개선 제안 순서로 정리해요.", "PDF 리포트 1부"), daysAgo(9));
            JobSubmission reviewsDraft = submitDraft(reviews, lee, "리뷰 리포트 초안이에요.",
                    "1240x1754.png?text=Report+Draft");
            requestRevision(reviews, reviewsDraft, "포장 관련 의견을 따로 한 장으로 모아 주세요.");
            submitRevision(reviews, lee, "포장 의견을 따로 모은 수정안이에요.", "1240x1754.png?text=Report+v2");

            Job posts = openJob(myStore, "인스타 게시물 5개 제작",
                    "가게 계정에 올릴 게시물 5개를 만들어 주세요.", 75_000L, -40, -33, 1, "SNS 게시물");
            match(myStore, posts, apply(posts, me, "가게 사진으로 통일감 있는 게시물 5개를 만들게요",
                    "주제 정하기, 사진 보정, 문구 쓰기 순서로 진행해요.", "PNG 5장"), daysAgo(45));
            complete(posts, submitDraft(posts, me, "게시물 5개예요.",
                    "1080x1080.png?text=Post+1", "1080x1350.png?text=Post+2"));
            review(posts, me, 5, "사진 보정이 깔끔하고 일정도 딱 맞춰 주셨어요.",
                    ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY,
                    ReviewPositivePoint.FAST_COMMUNICATION);

            Job signboard = openJob(myStore, "가게 앞 입간판 시안",
                    "가게 앞에 세울 입간판 시안을 만들어 주세요.", 55_000L, -25, -18, 1, "간판·현수막 시안");
            match(myStore, signboard, apply(signboard, kim, "멀리서도 보이는 입간판으로 만들게요",
                    "글씨 크기를 먼저 정하고 색 조합 2가지를 보여 드려요.", "인쇄용 PDF"), daysAgo(30));
            JobSubmission signDraft = submitDraft(signboard, kim, "입간판 시안이에요.",
                    "1200x1800.png?text=Sign+Draft");
            requestRevision(signboard, signDraft, "가게 이름을 조금 더 크게 해 주세요.");
            complete(signboard, submitRevision(signboard, kim, "가게 이름을 키웠어요.",
                    "1200x1800.png?text=Sign+Final"));

            Job sticker = openJob(myStore, "포장 스티커 디자인",
                    "포장 용기에 붙일 원형 스티커를 만들어 주세요.", 35_000L, 3, 9, 1, "쿠폰·스티커·명함 디자인");
            match(myStore, sticker, apply(sticker, choi, "가게 로고를 살린 원형 스티커를 만들게요",
                    "로고 정리 후 시안 2가지를 드려요.", "인쇄용 PDF"), daysAgo(10));
            jobService.cancelJob(CancelJobCommand.of(null, sticker.getId(),
                    "가게 사정으로 디자인을 잠시 미루게 됐어요.", "그동안 고민해 주셔서 감사해요."), myStore.getId());
            paymentService.refundOnCancel(sticker.getId());
        }

        /** 방문자 가게가 받은 제안: 결정 대기 · 결제 완료(학생 시작 전) · 작업 중 · 학생 거절. */
        private void seedMyStoreProposals(Student kim, Student park, Student lee, Student nuri, Student choi) {
            Proposal discount = propose(me, myStore, "시험 기간 학생 할인 이벤트",
                    "시험 기간에는 학생 손님이 줄어들어요.",
                    "학생증을 보여 주면 음료를 할인해 주는 이벤트를 기획하고 홍보물까지 만들어 드려요.",
                    "1일차 이벤트 조건 정하기, 2~3일차 홍보물 만들기", 30_000L, 3, 7, "홍보·이벤트 기획");
            like(discount, lee, nuri, choi);

            Proposal translation = propose(choi, myStore, "영어 메뉴판 번역",
                    "외국인 손님이 메뉴를 고르기 어려워해요.",
                    "메뉴 이름과 설명을 영어로 옮기고 매운 정도를 그림으로 표시해 드려요.",
                    "메뉴 정리, 번역, 검수 순서로 진행해요.", 40_000L, 4, 8, "영어 번역");
            like(translation, kim);

            Proposal lunchMenu = propose(me, myStore, "점심 세트 메뉴판 정리",
                    "점심 메뉴가 많아 손님이 고르는 데 오래 걸려요.",
                    "점심 세트 3가지를 한눈에 보이게 정리한 메뉴판을 만들어 드려요.",
                    "세트 구성 정리, 시안, 최종본 순서로 진행해요.", 45_000L, 3, 7, "메뉴판·가격표 디자인");
            pay(lunchMenu, myStore, 45_000L, 1, "점심 손님이 많아서 기대돼요!", daysAgo(1));

            Proposal intro = propose(me, myStore, "가게 소개글 다시 쓰기",
                    "지도 앱의 가게 소개글이 오래돼서 지금 메뉴와 맞지 않아요.",
                    "지금 메뉴와 분위기가 드러나는 소개글을 새로 써 드려요.",
                    "가게 인터뷰, 초안, 다듬기 순서로 진행해요.", 30_000L, 4, 8, "소개·공지 글쓰기");
            start(intro, pay(intro, myStore, 30_000L, 1, "편하게 써 주세요.", daysAgo(3)));

            Proposal groupOrder = propose(park, myStore, "단체 주문 안내문 디자인",
                    "단체 주문 방법을 묻는 전화가 많아요.",
                    "단체 주문 방법과 할인을 한 장에 정리한 안내문을 만들어 드려요.",
                    "내용 정리, 시안, 최종본 순서로 진행해요.", 35_000L, 3, 7, "전단지·포스터 디자인");
            decline(groupOrder, pay(groupOrder, myStore, 35_000L, 1, null, daysAgo(8)));
        }

        /** 다른 가게: 방문자 학생의 작업·지원·선택되지 않은 지원, 공감할 다른 학생의 제안, 탐색에 보일 의뢰. */
        private void seedOtherStores(Owner dino, Owner kwCafe, Owner banjeom, Owner chicken,
                                     Student kim, Student park, Student lee, Student nuri, Student choi) {
            Job stickers = openJob(dino, "쿠폰·스티커 디자인",
                    "공룡 캐릭터가 들어간 쿠폰과 포장 스티커를 만들어 주세요.",
                    50_000L, 2, 9, 2, "쿠폰·스티커·명함 디자인");
            match(dino, stickers, apply(stickers, me, "공룡 캐릭터를 살린 쿠폰과 스티커를 만들게요",
                    "캐릭터 정리, 쿠폰 시안, 스티커 시안 순서로 진행해요.", "인쇄용 PDF 2개"), daysAgo(5));
            submitDraft(stickers, me, "쿠폰과 스티커 초안이에요.",
                    "1080x1080.png?text=Dino+Coupon", "1080x1350.png?text=Dino+Sticker");
            chat(stickers, me, "초안 올렸어요. 확인 부탁드려요!", null);

            Job menuTranslation = openJob(kwCafe, "영어·중국어 메뉴판 번역",
                    "외국인 학생 손님을 위해 메뉴판을 영어와 중국어로 옮겨 주세요.",
                    60_000L, 5, 12, 1, "영어 번역", "중국어 번역");
            apply(menuTranslation, me, "영어·중국어 메뉴판을 한 장에 정리해 드릴게요",
                    "메뉴 정리, 번역, 원어민 검수 순서로 진행해요.", "인쇄용 PDF와 원본 파일");
            apply(menuTranslation, choi, "자연스러운 영어 메뉴 이름으로 옮겨 드려요",
                    "메뉴 설명을 짧게 다듬어 번역해요.", "PDF 1부");

            Job banner = openJob(banjeom, "배달 리뷰 이벤트 배너",
                    "배달앱 리뷰 이벤트를 알리는 배너를 만들어 주세요.", 45_000L, 3, 9, 1, "전단지·포스터 디자인");
            apply(banner, me, "눈에 띄는 리뷰 이벤트 배너를 만들게요",
                    "문구 정하기, 시안 2가지, 최종본 순서로 진행해요.", "배달앱 규격 이미지 2장");
            match(banjeom, banner, apply(banner, nuri, "배달앱 화면에 맞춘 배너를 만들어 드려요",
                    "앱별 규격을 맞춰 두 가지 크기로 드려요.", "PNG 2장"), daysAgo(2));

            openJob(chicken, "전화 대신 받는 예약서 만들기",
                    "전화 주문이 많아 바빠요. 손님이 직접 쓰는 온라인 예약서를 만들어 주세요.",
                    90_000L, 7, 14, 1, "온라인 예약·주문서");

            Job poster = openJob(dino, "여름 음료 포스터", "여름 신메뉴 음료 포스터를 만들어 주세요.",
                    40_000L, -22, -16, 1, "전단지·포스터 디자인");
            match(dino, poster, apply(poster, choi, "시원한 느낌의 음료 포스터를 만들게요",
                    "색 정하기, 시안, 최종본 순서로 진행해요.", "A3 인쇄용 PDF"), daysAgo(20));
            complete(poster, submitDraft(poster, choi, "음료 포스터예요.", "1191x1684.png?text=Summer+Poster"));
            review(poster, choi, 4, "색감이 시원해서 손님들이 많이 물어봤어요.", ReviewPositivePoint.QUALITY_OUTPUT);

            Proposal menuDraft = propose(lee, banjeom, "인기 메뉴를 강조한 메뉴판 시안",
                    "메뉴가 많아 대표 메뉴가 눈에 띄지 않아요.",
                    "주문이 많은 메뉴 5가지를 위로 올리고 사진을 크게 넣은 메뉴판을 만들어 드려요.",
                    "주문 기록 보기, 시안, 최종본 순서로 진행해요.", 35_000L, 3, 7, "메뉴판·가격표 디자인");
            like(menuDraft, kim, park, nuri, choi);

            Proposal examEvent = propose(nuri, kwCafe, "시험 기간 광운대생 이벤트 기획",
                    "시험 기간에 자리가 비어 있는 시간이 많아요.",
                    "시험 기간 늦은 시간 할인과 SNS 인증 이벤트를 기획해 드려요.",
                    "이벤트 조건 정하기, 홍보물 만들기, SNS 안내 순서로 진행해요.", 30_000L, 3, 7, "홍보·이벤트 기획");
            like(examEvent, lee, choi);

            Proposal photos = propose(kim, chicken, "치킨 세트 사진 다시 찍기",
                    "배달앱 메뉴 사진이 어두워서 맛있어 보이지 않아요.",
                    "세트 메뉴 4가지를 밝게 다시 찍고 보정해 드려요.",
                    "촬영 날짜 정하기, 촬영, 보정 순서로 진행해요.", 50_000L, 5, 10, "음식·매장 사진");
            like(photos, park);
        }

        private Student student(int number, String name, String major, String introduction, String... specialties) {
            User user = userService.createDemoSampleUser(demoSessionId, UserRole.STUDENT, number, name);
            Student student = studentService.createStudentProfile(CreateStudentProfileCommand.of(
                    user.getId(), UNIVERSITY, DemoStudentNumbers.next(studentService), major, null, introduction, null));
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

        private Job openJob(Owner store, String title, String description, long budget, int draftInDays,
                            int finalInDays, int revisionCount, String... specialties) {
            return jobService.createJob(CreateJobCommand.of(store.getId(), specialties(specialties), title, description,
                    budget, today.plusDays(draftInDays), today.plusDays(finalInDays), revisionCount), demoSessionId);
        }

        private JobApplication apply(Job job, Student student, String summary, String workPlan, String deliveryMethod) {
            return jobService.createJobApplication(CreateJobApplicationCommand.of(
                    null, job.getId(), summary, workPlan, deliveryMethod), student.getId(), demoSessionId);
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
        }

        private JobSubmission submitDraft(Job job, Student student, String message, String... files) {
            return jobService.submitDraft(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId());
        }

        private JobSubmission submitRevision(Job job, Student student, String message, String... files) {
            return jobService.submitRevision(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId());
        }

        private void requestRevision(Job job, JobSubmission submission, String message) {
            jobService.requestRevision(RequestJobSubmissionRevisionCommand.of(
                    null, job.getId(), submission.getId(), message, List.of()), job.getOwnerProfileId());
        }

        private void complete(Job job, JobSubmission submission) {
            jobService.completeSubmission(CompleteJobSubmissionCommand.of(
                    job.getId(), submission.getId(), job.getOwnerProfileId()));
        }

        private void review(Job job, Student student, int rating, String content, ReviewPositivePoint... points) {
            reviewService.createReview(CreateReviewCommand.of(null, job.getId(), List.of(points), content, rating),
                    job.getOwnerProfileId(), student.getId());
        }

        /** 학생이 먼저 보낸 메시지를 사장님이 읽었고, 사장님 답장은 학생이 아직 읽지 않은 채팅방. */
        private void chat(Job job, Student student, String studentMessage, String ownerReply) {
            ChatRoom room = chatRoomService.getOrCreate(job.getId());
            Long studentMessageId = chatService.sendTextMessage(
                    room, student.getUserId(), UUID.randomUUID(), studentMessage).getMessage().getId();
            if (ownerReply != null) {
                Owner store = ownerService.getOwnerProfileById(job.getOwnerProfileId());
                chatService.markRead(room, true, studentMessageId);
                chatService.sendTextMessage(room, store.getUserId(), UUID.randomUUID(), ownerReply);
            }
        }

        private Proposal propose(Student student, Owner store, String title, String customerProblem,
                                 String proposedSolution, String workPlan, long proposedFee, int draftDays,
                                 int finalDays, String... specialties) {
            return proposalService.createProposal(CreateProposalCommand.of(
                    null, store.getId(), specialties(specialties), title, customerProblem, proposedSolution, workPlan,
                    proposedFee, draftDays, finalDays, List.of()), student.getId(), demoSessionId);
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
            return job;
        }

        /** 학생이 의뢰서 조건을 보고 작업을 시작한 상태. ProposalFacade.startProposalJob과 같다. */
        private void start(Proposal proposal, Job job) {
            jobService.startJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.accept();
            chatRoomService.getOrCreate(job.getId());
        }

        /** 학생이 의뢰서를 거절해 전액 환불된 상태. ProposalFacade.declineProposalJob과 같다. */
        private void decline(Proposal proposal, Job job) {
            Owner store = ownerService.getOwnerProfileById(proposal.getOwnerProfileId());
            jobService.declineJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.reject();
            paymentService.refundOnDecline(job.getId(), proposal.getId(), store.getUserId());
        }

        private List<Long> specialties(String... names) {
            return Arrays.stream(names).filter(specialtyIds::containsKey).map(specialtyIds::get).toList();
        }

        private List<String> sampleFiles(String... files) {
            return Arrays.stream(files).map(file -> SAMPLE_FILE_BASE + file).toList();
        }

        private Instant daysAgo(int days) {
            return now.minus(Duration.ofDays(days));
        }

        // kakao_tid는 고유한 20자 이하 값이어야 한다
        private String newTid() {
            return "DEMO" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
